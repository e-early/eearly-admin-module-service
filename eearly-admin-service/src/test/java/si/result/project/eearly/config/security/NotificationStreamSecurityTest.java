package si.result.project.eearly.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.notification.NotificationMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationType;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.notification.SseNotificationPublisher;
import si.result.project.eearly.resource.notification.NotificationSseController;
import si.result.project.eearly.sse.SseEmitterRegistry;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Who may open the notification stream: every authenticated caller with a valid bearer token in the
 * {@code Authorization} header (there is no role rule, as for the other notification endpoints); anonymous callers,
 * invalid tokens and a token in the URL are rejected with 401. The stream belongs to the caretaker of the token.
 * The real controller and registry run here, with the caretaker service mocked.
 */
@WebMvcTest(NotificationSseController.class)
@ContextConfiguration(classes = {SecurityConfig.class, NotificationSseController.class, SseEmitterRegistry.class,
        NotificationMapperImpl.class, NotificationStreamSecurityTest.Auth.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class})
@TestPropertySource(properties = {
        "spring.config.name=notification-stream-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class NotificationStreamSecurityTest {

    @TestConfiguration
    static class Auth {
        @Bean
        AuthenticationProvider authenticationProvider() {
            return new AuthenticationProviderImpl();
        }
    }

    private static final String STREAM = "/api/v1/notifications/stream";
    private static final UUID ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    private static final UUID KEYCLOAK_ID = UUID.fromString("0c6c3b3e-8d6e-4d9a-9a52-1d6f6a8e2b11");
    private static final UUID OTHER_ID = UUID.fromString("6f1a1c5e-3a52-4c0f-8c1f-0a0f6f0c7d22");
    private static final UUID OTHER_KEYCLOAK_ID = UUID.fromString("a9d0a7c4-5b19-4e0c-b6f4-3c1d7f9b8e33");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseNotificationPublisher publisher;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CaretakerService caretakerService;

    @BeforeEach
    void setUp() throws Exception {
        token("caretaker-token", KEYCLOAK_ID.toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("other-caretaker-token", OTHER_KEYCLOAK_ID.toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("admin-token", KEYCLOAK_ID.toString(), "{\"test-client\": {\"roles\": [\"Administrator\"]}}");
        token("no-roles-token", KEYCLOAK_ID.toString(), "{}");
        token("not-a-caretaker-token", UUID.randomUUID().toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));

        final Caretaker caretaker = caretaker(ID);
        final Caretaker otherCaretaker = caretaker(OTHER_ID);
        when(caretakerService.findByKeycloakId(KEYCLOAK_ID)).thenReturn(caretaker);
        when(caretakerService.findByKeycloakId(OTHER_KEYCLOAK_ID)).thenReturn(otherCaretaker);
        when(caretakerService.findByKeycloakId(org.mockito.ArgumentMatchers.argThat(
                id -> !KEYCLOAK_ID.equals(id) && !OTHER_KEYCLOAK_ID.equals(id))))
                .thenThrow(new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND));
    }

    private static Caretaker caretaker(UUID id) {
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(id);
        return caretaker;
    }

    private void token(String value, String subject, String resourceAccess) throws Exception {
        String claims = "{\"sub\": \"" + subject + "\", \"resource_access\": " + resourceAccess + "}";
        var parsed = JWTClaimsSet.parse(claims).getClaims();
        when(jwtDecoder.decode(value)).thenReturn(
                Jwt.withTokenValue(value).header("alg", "RS256").claims(c -> c.putAll(parsed)).build());
    }

    private MvcResult open(String bearer) throws Exception {
        return mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private Notification notification(UUID addressedTo) {
        return Notification.create(new CreateNotificationCommand(
                NotificationType.ERROR, "ANALYSIS_ERROR", UUID.randomUUID(), addressedTo, UUID.randomUUID()));
    }

    @Test
    @DisplayName("Anonymous callers are rejected with 401 and open no stream")
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(STREAM)).andExpect(status().isUnauthorized());
        verifyNoInteractions(caretakerService);
    }

    @Test
    @DisplayName("An invalid token is rejected with 401")
    void invalidToken_isUnauthorized() throws Exception {
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(caretakerService);
    }

    @Test
    @DisplayName("A token in the query string is not accepted: only the Authorization header authenticates")
    void tokenInQueryString_isNotAccepted() throws Exception {
        mockMvc.perform(get(STREAM + "?access_token=caretaker-token").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(STREAM + "?token=caretaker-token").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(caretakerService);
    }

    @Test
    @DisplayName("A token in a cookie is not accepted")
    void tokenInCookie_isNotAccepted() throws Exception {
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "caretaker-token")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(caretakerService);
    }

    @Test
    @DisplayName("A valid token opens the stream, whatever the roles")
    void validToken_opensTheStream() throws Exception {
        for (String bearer : java.util.List.of("caretaker-token", "admin-token", "no-roles-token")) {
            final MvcResult stream = open(bearer);

            assertThat(stream.getResponse().getStatus()).isEqualTo(200);
            assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
            assertThat(stream.getResponse().getContentAsString()).contains(":connected");
        }
    }

    @Test
    @DisplayName("An authenticated caller who is not a caretaker gets no stream")
    void notACaretaker_isNotFound() throws Exception {
        // No Accept header: the error body is JSON, which a client that accepts nothing but event streams could not read.
        mockMvc.perform(get(STREAM).header("Authorization", "Bearer not-a-caretaker-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The stream receives the notifications of the caretaker of the token only")
    void stream_receivesTheNotificationsOfTheTokenCaretakerOnly() throws Exception {
        final MvcResult own = open("caretaker-token");
        final MvcResult other = open("other-caretaker-token");

        publisher.broadcast(notification(ID));

        assertThat(own.getResponse().getContentAsString()).contains("event:notification").contains("ANALYSIS_ERROR");
        assertThat(other.getResponse().getContentAsString()).doesNotContain("event:notification");
    }

    @Test
    @DisplayName("The publisher of the application is the registry")
    void publisher_isTheRegistry() {
        assertThat(publisher).isInstanceOf(SseEmitterRegistry.class);
    }
}
