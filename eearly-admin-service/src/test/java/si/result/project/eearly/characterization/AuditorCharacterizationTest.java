package si.result.project.eearly.characterization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import si.result.project.eearly.config.auditing.AuditorAwareImpl;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;
import si.result.spring.boot.bricks.auth.PublicAuthenticationProviderImpl;

/**
 * Characterization of the auditor resolution: {@link AuditorAwareImpl} (this service) on top of the
 * {@link AuthenticationProvider} from {@code si.result.lib:spring-boot-bricks}.
 *
 * The application scans {@code @ComponentScan("si.result")}, so both bricks implementations are candidates; the
 * profile expressions on them decide: {@link AuthenticationProviderImpl} for {@code !(public | unrestricted)}
 * (the default and the "development" profile), {@link PublicAuthenticationProviderImpl} for
 * {@code public | unrestricted}.
 */
class AuditorCharacterizationTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static JwtAuthenticationToken jwtAuth(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").claims(c -> c.putAll(claims)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_admin")), "principal-name");
    }

    private static Optional<String> auditor(AuthenticationProvider provider) {
        return new AuditorAwareImpl(provider).getCurrentAuditor();
    }

    private static AnnotationConfigApplicationContext contextWithProfiles(String... profiles) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().setActiveProfiles(profiles);
        // same sub-package the application picks up through @ComponentScan("si.result"), limited to the auth beans
        ctx.scan("si.result.spring.boot.bricks.auth");
        ctx.refresh();
        return ctx;
    }

    // ---- which provider the application injects ----

    @Test
    void defaultProfileInjectsAuthenticationProviderImpl() {
        try (AnnotationConfigApplicationContext ctx = contextWithProfiles()) {
            assertThat(ctx.getBeansOfType(AuthenticationProvider.class)).hasSize(1);
            assertThat(ctx.getBean(AuthenticationProvider.class)).isExactlyInstanceOf(AuthenticationProviderImpl.class);
        }
    }

    @Test
    void developmentProfileInjectsAuthenticationProviderImpl() {
        try (AnnotationConfigApplicationContext ctx = contextWithProfiles("development")) {
            assertThat(ctx.getBean(AuthenticationProvider.class)).isExactlyInstanceOf(AuthenticationProviderImpl.class);
        }
    }

    @Test
    void publicAndUnrestrictedProfilesInjectPublicAuthenticationProviderImpl() {
        for (String profile : new String[] {"public", "unrestricted"}) {
            try (AnnotationConfigApplicationContext ctx = contextWithProfiles(profile)) {
                assertThat(ctx.getBeansOfType(AuthenticationProvider.class)).hasSize(1);
                assertThat(ctx.getBean(AuthenticationProvider.class))
                        .isExactlyInstanceOf(PublicAuthenticationProviderImpl.class);
            }
        }
    }

    // ---- default provider (AuthenticationProviderImpl) with AuditorAwareImpl ----

    @Test
    void jwtAuthenticationYieldsTheNameClaimAsAuditor() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(Map.of(
                "sub", "user-123",
                "name", "Jane Doe",
                "given_name", "Jane",
                "family_name", "Doe",
                "preferred_username", "jdoe")));

        assertThat(auditor(new AuthenticationProviderImpl())).contains("Jane Doe");
    }

    @Test
    void auditorBeanFunctionDelegatesToTheSameResolution() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(Map.of("sub", "user-123", "name", "Jane Doe")));

        AuditorAware<String> aware = new AuditorAwareImpl(new AuthenticationProviderImpl()).auditorAware();

        assertThat(aware.getCurrentAuditor()).contains("Jane Doe");
    }

    @Test
    void providerReadsIdNameFirstAndLastNameFromTokenAttributesAndAuthoritiesFromContext() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(Map.of(
                "sub", "user-123",
                "name", "Jane Doe",
                "given_name", "Jane",
                "family_name", "Doe",
                "preferred_username", "jdoe")));
        AuthenticationProvider provider = new AuthenticationProviderImpl();

        assertThat(provider.getId()).isEqualTo("user-123");
        assertThat(provider.getName()).isEqualTo("Jane Doe");
        assertThat(provider.getFirstName()).isEqualTo("Jane");
        assertThat(provider.getLastName()).isEqualTo("Doe");
        assertThat(provider.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_admin");
    }

    @Test
    void preferredUsernameAndSubAreNotUsedAsAuditor() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(Map.of(
                "sub", "user-123", "preferred_username", "jdoe", "given_name", "Jane", "family_name", "Doe")));

        // no "name" claim -> provider returns null -> no auditor (created_by/updated_by stay null)
        assertThat(auditor(new AuthenticationProviderImpl())).isEmpty();
    }

    @Test
    void noAuthenticationYieldsNoAuditor() {
        SecurityContextHolder.clearContext();

        assertThat(auditor(new AuthenticationProviderImpl())).isEmpty();
    }

    @Test
    void nonJwtAuthenticationYieldsNoAuditor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jdoe", "n/a", AuthorityUtils.createAuthorityList("ROLE_user")));

        assertThat(auditor(new AuthenticationProviderImpl())).isEmpty();
    }

    @Test
    void anonymousAuthenticationYieldsNoAuditor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(auditor(new AuthenticationProviderImpl())).isEmpty();
    }

    @Test
    void currentDefectProviderGettersThrowWithoutAuthenticationAndAuditorSwallowsIt() {
        // Defect-like: the provider itself NPEs / ClassCastExceptions without a JWT; only AuditorAwareImpl catches it.
        SecurityContextHolder.clearContext();
        AuthenticationProvider provider = new AuthenticationProviderImpl();

        assertThatThrownBy(provider::getName).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(provider::getId).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(provider::getAuthorities).isInstanceOf(NullPointerException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jdoe", "n/a", AuthorityUtils.createAuthorityList("ROLE_user")));
        assertThatThrownBy(provider::getName).isInstanceOf(ClassCastException.class);
    }

    @Test
    void nonStringNameClaimYieldsNoAuditor() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(Map.of("sub", "user-123", "name", 42)));

        assertThat(auditor(new AuthenticationProviderImpl())).isEmpty();
    }

    // ---- public / unrestricted provider ----

    @Test
    void publicProviderReturnsFixedAnonymousIdentityRegardlessOfSecurityContext() {
        PublicAuthenticationProviderImpl provider = new PublicAuthenticationProviderImpl();

        assertThat(provider.getName()).isEqualTo("Anonymous");
        assertThat(provider.getFirstName()).isEqualTo("Anonymous");
        assertThat(provider.getLastName()).isEqualTo("Anonymous");
        assertThat(provider.getId()).isEqualTo("cc7f8d0b-f749-42c4-8b1e-95295307a6c4");
        assertThat(provider.getAuthorities()).isEmpty();
    }

    @Test
    void publicProviderMakesAuditorAnonymousEvenWithAJwtPresent() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(Map.of("sub", "user-123", "name", "Jane Doe")));

        assertThat(auditor(new PublicAuthenticationProviderImpl())).contains("Anonymous");
    }

    @Test
    void publicProviderMakesAuditorAnonymousWithoutAuthentication() {
        assertThat(auditor(new PublicAuthenticationProviderImpl())).contains("Anonymous");
    }
}
