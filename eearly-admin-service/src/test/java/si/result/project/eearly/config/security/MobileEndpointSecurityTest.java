package si.result.project.eearly.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;

/**
 * {@code /api/v1/mobile/*} is open to anonymous callers in {@link SecurityConfig}. The service has no endpoint there:
 * what an anonymous caller gets is the answer for a path nothing handles, and nothing of the service's data. How the
 * app is set up from a QR code is the mobile backend's business (its onboarding endpoints), not this service's.
 */
@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, MobileEndpointSecurityTest.Auth.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.config.name=mobile-endpoint-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class MobileEndpointSecurityTest {

    @TestConfiguration
    static class Auth {
        @Bean
        AuthenticationProvider authenticationProvider() {
            return new AuthenticationProviderImpl();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("No controller of the service maps a path under /api/v1/mobile")
    void noEndpointIsMappedUnderMobile() throws Exception {
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        final List<String> paths = new ArrayList<>();
        int controllers = 0;
        for (final BeanDefinition definition : scanner.findCandidateComponents("si.result.project.eearly")) {
            controllers++;
            final Class<?> controller = Class.forName(definition.getBeanClassName());
            collect(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class), paths);
            for (final Method method : controller.getDeclaredMethods()) {
                collect(AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class), paths);
            }
        }

        // The scan sees the controllers (it would be vacuous otherwise) ...
        assertThat(controllers).isGreaterThan(5);
        assertThat(paths).contains("/api/v1/qrcodes", "/api/v1/patients");
        // ... and none of them is under the open path.
        assertThat(paths).noneMatch(path -> path.startsWith("/api/v1/mobile"));
    }

    private static void collect(final RequestMapping mapping, final List<String> paths) {
        if (mapping != null) {
            paths.addAll(List.of(mapping.path()));
            paths.addAll(List.of(mapping.value()));
        }
    }

    @Test
    @DisplayName("An anonymous caller is let through to /api/v1/mobile/<one segment>, where nothing answers")
    void anonymous_reachesNothingUnderMobile() throws Exception {
        for (final String path : new String[] {"/api/v1/mobile/token", "/api/v1/mobile/onboarding", "/api/v1/mobile/1"}) {
            for (final var request : List.of(get(path), post(path).contentType(MediaType.APPLICATION_JSON).content("{}"),
                    put(path).contentType(MediaType.APPLICATION_JSON).content("{}"), delete(path))) {
                final var result = mockMvc.perform(request).andReturn().getResponse();

                // Not turned away by the security rules, and nothing but an error answers.
                assertThat(result.getStatus()).isNotIn(200, 201, 204, 401, 403);
                assertThat(result.getContentAsString()).doesNotContain("token\":\"").doesNotContain("payload");
            }
        }
    }

    @Test
    @DisplayName("Anything else, including deeper paths under /api/v1/mobile, needs authentication")
    void everythingElse_needsAuthentication() throws Exception {
        for (final String path : new String[] {"/api/v1/mobile", "/api/v1/mobile/a/b",
                "/api/v1/mobiles/a", "/api/v1/internal/patients/1/language", "/api/v1/qrcodes", "/api/v1/patients",
                "/api/v1/onboarding/1"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
