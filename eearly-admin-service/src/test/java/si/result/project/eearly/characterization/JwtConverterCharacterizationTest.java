package si.result.project.eearly.characterization;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import si.result.spring.boot.bricks.auth.jwt.JwtConverter;

/**
 * Characterization of {@link JwtConverter} from {@code si.result.lib:spring-boot-bricks}, instantiated with its
 * default constructor exactly as in {@code SecurityConfig} (principal claim "sub", roles at
 * "realm_access.roles", authority prefix "ROLE_").
 *
 * The roles lookup only descends into nested claims that are Nimbus' shaded Gson {@code LinkedTreeMap}, which is
 * what {@link NimbusJwtDecoder} produces for JSON objects. Contract tests therefore decode a real HS256-signed
 * token (test secret only) or use {@code LinkedTreeMap} explicitly.
 */
class JwtConverterCharacterizationTest {

    private static final byte[] TEST_SECRET = "0123456789-0123456789-0123456789".getBytes(StandardCharsets.UTF_8);

    private final JwtConverter converter = new JwtConverter();

    /** Signs and decodes a token the way the resource server does, so nested JSON objects get their real types. */
    private static Jwt decode(String claimsJson) throws Exception {
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.parse(claimsJson));
        signed.sign(new MACSigner(TEST_SECRET));
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(TEST_SECRET, "HmacSHA256"))
                .build().decode(signed.serialize());
    }

    private static List<String> authorityNames(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token").header("alg", "none").claims(c -> c.putAll(claims)).build();
    }

    private static LinkedTreeMap<String, Object> treeMap(String key, Object value) {
        LinkedTreeMap<String, Object> map = new LinkedTreeMap<>();
        map.put(key, value);
        return map;
    }

    // ---- real decoded token ----

    @Test
    void decodedTokenMapsSubToPrincipalAndRealmRolesToPrefixedAuthorities() throws Exception {
        Jwt jwt = decode("{\"sub\":\"user-1\",\"realm_access\":{\"roles\":[\"admin\",\"caretaker\"]}}");

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getName()).isEqualTo("user-1");
        assertThat(token.getPrincipal()).isEqualTo(jwt);
        assertThat(authorityNames(token)).containsExactly("ROLE_admin", "ROLE_caretaker");
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    void decodedTokenNestedClaimIsLinkedTreeMap() throws Exception {
        Jwt jwt = decode("{\"sub\":\"user-1\",\"realm_access\":{\"roles\":[\"admin\"]}}");

        assertThat(jwt.getClaims().get("realm_access")).isInstanceOf(LinkedTreeMap.class);
    }

    @Test
    void decodedTokenWithoutRolesHasNoAuthoritiesButKeepsPrincipal() throws Exception {
        AbstractAuthenticationToken token = converter.convert(decode("{\"sub\":\"user-2\"}"));

        assertThat(token.getName()).isEqualTo("user-2");
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void decodedTokenWithEmptyRolesOrRealmAccessWithoutRolesHasNoAuthorities() throws Exception {
        assertThat(converter.convert(decode("{\"sub\":\"u\",\"realm_access\":{\"roles\":[]}}")).getAuthorities()).isEmpty();
        assertThat(converter.convert(decode("{\"sub\":\"u\",\"realm_access\":{}}")).getAuthorities()).isEmpty();
    }

    @Test
    void decodedTokenWithNonCollectionRolesHasNoAuthorities() throws Exception {
        assertThat(converter.convert(decode("{\"sub\":\"u\",\"realm_access\":{\"roles\":\"admin\"}}")).getAuthorities())
                .isEmpty();
    }

    @Test
    void resourceAccessClientRolesAreIgnored() throws Exception {
        Jwt jwt = decode("{\"sub\":\"u\",\"realm_access\":{\"roles\":[\"user\"]},"
                + "\"resource_access\":{\"eearly-admin\":{\"roles\":[\"client-admin\"]}}}");

        assertThat(authorityNames(converter.convert(jwt))).containsExactly("ROLE_user");
    }

    @Test
    void rolesKeepTheirCaseAndAreDeduplicated() throws Exception {
        Jwt jwt = decode("{\"sub\":\"u\",\"realm_access\":{\"roles\":[\"Admin\",\"admin\",\"admin\"]}}");

        assertThat(authorityNames(converter.convert(jwt))).containsExactly("ROLE_Admin", "ROLE_admin");
    }

    @Test
    void principalNameIsSubNotPreferredUsername() throws Exception {
        Jwt jwt = decode("{\"sub\":\"user-3\",\"preferred_username\":\"jdoe\",\"name\":\"John Doe\"}");

        assertThat(converter.convert(jwt).getName()).isEqualTo("user-3");
    }

    @Test
    void currentDefectTokenWithoutSubClaimIsAcceptedWithNullPrincipalName() throws Exception {
        // Defect-like: an authenticated token whose name is null; downstream code relying on getName() gets null.
        Jwt jwt = decode("{\"preferred_username\":\"jdoe\",\"realm_access\":{\"roles\":[\"user\"]}}");

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isNull();
        assertThat(authorityNames(token)).containsExactly("ROLE_user");
    }

    @Test
    void nullSourceGivesNullAuthentication() {
        assertThat(converter.convert(null)).isNull();
    }

    // ---- hand-built Jwt with LinkedTreeMap ----

    @Test
    void linkedTreeMapRealmAccessMapsRoles() {
        Jwt jwt = jwtWithClaims(Map.of("sub", "user-4", "realm_access", treeMap("roles", List.of("admin", "user"))));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("user-4");
        assertThat(authorityNames(token)).containsExactly("ROLE_admin", "ROLE_user");
    }

    @Test
    void currentDefectPlainJavaMapRealmAccessYieldsNoRoles() {
        // Defect: the converter only recognises Nimbus' shaded gson LinkedTreeMap. A plain java.util.Map (e.g. from
        // another JWT decoder or a test fixture) silently yields no authorities, i.e. every role check would fail.
        Jwt jwt = jwtWithClaims(Map.of("sub", "user-5", "realm_access", Map.of("roles", List.of("admin"))));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("user-5");
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void customConstructorUsesGivenClaimNamesAndPrefix() {
        JwtConverter custom = new JwtConverter("preferred_username", "roles", "R_");
        Jwt jwt = jwtWithClaims(Map.of("sub", "s", "preferred_username", "jdoe", "roles", List.of("x")));

        AbstractAuthenticationToken token = custom.convert(jwt);

        assertThat(token.getName()).isEqualTo("jdoe");
        assertThat(authorityNames(token)).containsExactly("R_x");
    }
}
