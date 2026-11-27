package si.result.project.eearly.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    // Claims parsed as the resource server's JWT decoder (Nimbus) parses them.
    private static Jwt jwt(final String claimsJson) throws Exception {
        final var claims = JWTClaimsSet.parse(claimsJson).getClaims();
        return Jwt.withTokenValue("token").header("alg", "RS256").claims(c -> c.putAll(claims)).build();
    }

    private static Set<String> authorities(final String claimsJson) throws Exception {
        return SecurityConfig.jwtConverter("test-client").convert(jwt(claimsJson)).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("The configured client's roles become ROLE_ authorities")
    void jwtConverter_ShouldMapTheConfiguredClientRoles() throws Exception {
        assertEquals(Set.of("ROLE_MobileService"), authorities("""
                {"sub": "service-account-mobile",
                 "realm_access": {"roles": ["offline_access"]},
                 "resource_access": {"test-client": {"roles": ["MobileService"]},
                                     "account": {"roles": ["manage-account"]}}}
                """));
    }

    @Test
    @DisplayName("Another client's or a realm role is not the configured client's")
    void jwtConverter_ShouldIgnoreOtherClientsAndRealmRoles() throws Exception {
        assertTrue(authorities("""
                {"sub": "someone",
                 "realm_access": {"roles": ["MobileService"]},
                 "resource_access": {"account": {"roles": ["MobileService"]}}}
                """).isEmpty());
    }

    @Test
    @DisplayName("A token without client roles has no authorities")
    void jwtConverter_WithoutResourceAccess_ShouldHaveNoAuthorities() throws Exception {
        assertTrue(authorities("""
                {"sub": "someone"}
                """).isEmpty());
    }
}
