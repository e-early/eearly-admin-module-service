package si.result.project.eearly.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import si.result.logging.spring.boot.config.LoggingConfig;
import si.result.logging.spring.boot.config.ServicePropertiesConfig;
import si.result.logging.spring.boot.config.properties.LoggingConfigurationProperties;
import si.result.project.eearly.config.security.SecurityConfig;
import si.result.project.eearly.mapper.notification.NotificationMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.resource.notification.NotificationSseController;
import si.result.project.eearly.sse.SseEmitterRegistry;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;

/**
 * The notification stream with the service's request/response logging switched on, as in application.yaml
 * (HTTP and method logging enabled, Authorization header logging off): the stream is not held back by the response
 * logger, and a token never shows up in the log, neither one sent in the URL (which is not accepted) nor the one
 * in the Authorization header.
 */
@WebMvcTest(NotificationSseController.class)
@ContextConfiguration(classes = {SecurityConfig.class, NotificationSseController.class, SseEmitterRegistry.class,
        NotificationMapperImpl.class, NotificationStreamLoggingTest.Cfg.class, LoggingConfig.class,
        ServicePropertiesConfig.class, LoggingConfigurationProperties.class, SseLoggingBypassConfig.class})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.config.name=notification-stream-logging-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client",
        "logging.loggers.http.enabled=true",
        "logging.loggers.http.log-response=true",
        "logging.loggers.http.log-authorization-header=false",
        "logging.loggers.method.enabled=true"
})
class NotificationStreamLoggingTest {

    @TestConfiguration
    @EnableAspectJAutoProxy
    @EnableConfigurationProperties
    static class Cfg {
        @Bean
        AuthenticationProvider authenticationProvider() {
            return new AuthenticationProviderImpl();
        }

        @Bean
        BuildProperties buildProperties() {
            final Properties properties = new Properties();
            properties.setProperty("version", "test");
            return new BuildProperties(properties);
        }
    }

    private static final String STREAM = "/api/v1/notifications/stream";
    private static final UUID KEYCLOAK_ID = UUID.fromString("0c6c3b3e-8d6e-4d9a-9a52-1d6f6a8e2b11");
    private static final UUID CARETAKER_ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    // Distinctive values, so that any occurrence in the log output is the token itself.
    private static final String HEADER_TOKEN = "header-token-7d41c9e2b6a8";
    private static final String QUERY_TOKEN = "query-token-5b93f0a1d7c4";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseEmitterRegistry registry;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CaretakerService caretakerService;

    @BeforeEach
    void setUp() throws Exception {
        // Both tokens are valid, so a rejection is the endpoint's rule and not a failed decode.
        token(HEADER_TOKEN);
        token(QUERY_TOKEN);
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(CARETAKER_ID);
        when(caretakerService.findByKeycloakId(KEYCLOAK_ID)).thenReturn(caretaker);
    }

    private void token(String value) throws Exception {
        final String claims = "{\"sub\": \"" + KEYCLOAK_ID + "\", \"resource_access\": {\"test-client\": {\"roles\": [\"Clinician\"]}}}";
        final var parsed = JWTClaimsSet.parse(claims).getClaims();
        when(jwtDecoder.decode(value)).thenReturn(
                Jwt.withTokenValue(value).header("alg", "RS256").claims(c -> c.putAll(parsed)).build());
    }

    @Test
    @DisplayName("A request with the token in the query string is rejected and the token is in no log line")
    void tokenInQueryString_isRejectedAndNeverLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get(STREAM + "?access_token=" + QUERY_TOKEN).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(STREAM + "?access_token=" + QUERY_TOKEN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(STREAM + "?token=" + QUERY_TOKEN).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        assertThat(output.getAll()).doesNotContain(QUERY_TOKEN);
    }

    @Test
    @DisplayName("A stream opened with the Authorization header is logged without the token")
    void tokenInHeader_isNeverLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer " + HEADER_TOKEN))
                .andExpect(request().asyncStarted());

        // The request logging is on: the stream request itself is logged ...
        assertThat(output.getAll()).contains(STREAM);
        // ... and it does not contain the token.
        assertThat(output.getAll()).doesNotContain(HEADER_TOKEN);
    }

    @Test
    @DisplayName("The response logger does not hold back the stream, whatever the Accept header")
    void stream_isNotHeldBackByTheResponseLogger() throws Exception {
        for (final String accept : new String[] {MediaType.TEXT_EVENT_STREAM_VALUE, "*/*", null}) {
            final var request = get(STREAM).header("Authorization", "Bearer " + HEADER_TOKEN);
            if (accept != null) {
                request.header("Accept", accept);
            }
            final MvcResult stream = mockMvc.perform(request).andExpect(request().asyncStarted()).andReturn();
            assertThat(stream.getResponse().getContentAsString()).contains(":connected");

            registry.heartbeat();

            assertThat(stream.getResponse().getContentAsString()).contains(":ping");
        }
    }
}
