package si.result.project.eearly.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.facade.notification.NotificationServiceFacade;
import si.result.project.eearly.mapper.notification.NotificationMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.notification.command.CreateNotificationReadCommand;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.notification.NotificationService;
import si.result.project.eearly.resource.notification.NotificationController;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Who may call the notification endpoints: every authenticated caller (there is no role rule), anonymous and
 * invalid tokens are rejected with 401. The caretaker whose notifications are read or marked is always the
 * one the token subject (the caretaker's Keycloak id) belongs to; nothing in the request can change that. The
 * real facade runs here, with the domain services mocked.
 */
@WebMvcTest(NotificationController.class)
@ContextConfiguration(classes = {SecurityConfig.class, NotificationController.class, NotificationServiceFacade.class,
        NotificationMapperImpl.class, NotificationEndpointSecurityTest.Auth.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.config.name=notification-endpoint-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class NotificationEndpointSecurityTest {

    @TestConfiguration
    static class Auth {
        @Bean
        AuthenticationProvider authenticationProvider() {
            return new AuthenticationProviderImpl();
        }
    }

    private static final UUID ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    private static final UUID KEYCLOAK_ID = UUID.fromString("0c6c3b3e-8d6e-4d9a-9a52-1d6f6a8e2b11");
    private static final UUID OTHER_ID = UUID.fromString("6f1a1c5e-3a52-4c0f-8c1f-0a0f6f0c7d22");
    private static final UUID OTHER_KEYCLOAK_ID = UUID.fromString("a9d0a7c4-5b19-4e0c-b6f4-3c1d7f9b8e33");
    private static final UUID NOTIFICATION_ID = UUID.fromString("3b0d2f2e-8f0c-4a1e-9d4e-6a1d2c3b4a55");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationService notificationService;

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
        when(notificationService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(notificationService.findUnread(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
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

    private ResultActions perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    private List<ResultActions> allEndpoints(String bearer) throws Exception {
        return List.of(
                perform(get("/api/v1/notifications"), bearer),
                perform(get("/api/v1/notifications/unread"), bearer),
                perform(get("/api/v1/notifications/unread/count"), bearer),
                perform(post("/api/v1/notifications/read"), bearer),
                perform(post("/api/v1/notifications/read/" + NOTIFICATION_ID), bearer));
    }

    @Test
    @DisplayName("Anonymous callers are rejected with 401 on every endpoint and reach no service")
    void anonymous_isUnauthorized() throws Exception {
        for (ResultActions result : allEndpoints(null)) {
            result.andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(notificationService, caretakerService);
    }

    @Test
    @DisplayName("An invalid token is rejected with 401 on every endpoint")
    void invalidToken_isUnauthorized() throws Exception {
        for (ResultActions result : allEndpoints("invalid")) {
            result.andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(notificationService, caretakerService);
    }

    @Test
    @DisplayName("Any authenticated caretaker may use every endpoint, whatever their roles")
    void authenticatedCaretaker_isAllowed() throws Exception {
        for (String bearer : List.of("caretaker-token", "admin-token", "no-roles-token")) {
            for (ResultActions result : allEndpoints(bearer)) {
                result.andExpect(status().isOk());
            }
        }
    }

    @Test
    @DisplayName("The unread count is the one of the caretaker of the token")
    void unreadCount_isForTheTokenCaretaker() throws Exception {
        when(notificationService.getUnreadNotificationCountForCaretaker(ID)).thenReturn(4L);
        when(notificationService.getUnreadNotificationCountForCaretaker(OTHER_ID)).thenReturn(9L);

        perform(get("/api/v1/notifications/unread/count"), "caretaker-token")
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(4));
        perform(get("/api/v1/notifications/unread/count"), "other-caretaker-token")
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(9));
    }

    @Test
    @DisplayName("Lists are scoped to the caretaker of the token")
    void lists_areScopedToTheTokenCaretaker() throws Exception {
        perform(get("/api/v1/notifications"), "caretaker-token").andExpect(status().isOk());
        perform(get("/api/v1/notifications/unread"), "other-caretaker-token").andExpect(status().isOk());

        verify(notificationService).findAll(any(), any(), eq(ID));
        verify(notificationService).findUnread(any(), any(), eq(OTHER_ID));
        verify(notificationService, never()).findAll(any(), any(), eq(OTHER_ID));
    }

    @Test
    @DisplayName("Marking as read is done for the caretaker of the token, even if the request names another one")
    void markAsRead_isForTheTokenCaretaker() throws Exception {
        perform(post("/api/v1/notifications/read/" + NOTIFICATION_ID)
                .param("caretakerId", OTHER_ID.toString())
                .header("X-Caretaker-Id", OTHER_ID.toString()), "caretaker-token")
                .andExpect(status().isOk());

        final ArgumentCaptor<CreateNotificationReadCommand> command =
                ArgumentCaptor.forClass(CreateNotificationReadCommand.class);
        verify(notificationService).markNotificationAsRead(command.capture());
        assertThat(command.getValue().caretakerId()).isEqualTo(ID);
        assertThat(command.getValue().notificationId()).isEqualTo(NOTIFICATION_ID);
    }

    @Test
    @DisplayName("Marking all as read is done for the caretaker of the token only")
    void markAllAsRead_isForTheTokenCaretaker() throws Exception {
        perform(post("/api/v1/notifications/read").param("caretakerId", OTHER_ID.toString()), "caretaker-token")
                .andExpect(status().isOk());

        verify(notificationService).markAllNotificationsAsRead(ID);
        verify(notificationService, never()).markAllNotificationsAsRead(OTHER_ID);
    }

    @Test
    @DisplayName("A notification of another caretaker is reported as not found, as the service decides")
    void markingAnotherCaretakersNotification_isNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException(DomainExceptionCode.NOTIFICATION_NOT_FOUND))
                .when(notificationService).markNotificationAsRead(any());

        perform(post("/api/v1/notifications/read/" + NOTIFICATION_ID), "caretaker-token")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An authenticated caller who is not a caretaker sees nothing and changes nothing")
    void notACaretaker_isNotFound() throws Exception {
        for (ResultActions result : allEndpoints("not-a-caretaker-token")) {
            result.andExpect(status().isNotFound());
        }
        verifyNoInteractions(notificationService);
    }
}
