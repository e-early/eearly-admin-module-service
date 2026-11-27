package si.result.project.eearly.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.facade.caretaker.CaretakerServiceFacade;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.project.eearly.resource.caretaker.CaretakerController;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Who may call the caretaker endpoints: listing and creating are for the Administrator client role
 * of the configured client; reading and updating one caretaker are for that caretaker (the token's
 * subject is the caretaker's Keycloak id) or an administrator. The real facade runs here, with the
 * domain service mocked.
 */
@WebMvcTest(CaretakerController.class)
@ContextConfiguration(classes = {SecurityConfig.class, CaretakerController.class, CaretakerServiceFacade.class,
        CaretakerEndpointSecurityTest.Auth.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.config.name=caretaker-endpoint-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class CaretakerEndpointSecurityTest {

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
    private static final String BODY =
            "{\"firstName\":\"Test\",\"lastName\":\"Caretaker\",\"email\":\"test@example.org\",\"language\":\"sl\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CaretakerService caretakerService;

    @MockitoBean
    private CaretakerMapper caretakerMapper;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private DepartmentService departmentService;

    private Caretaker caretaker;
    private Caretaker otherCaretaker;

    @BeforeEach
    void setUp() throws Exception {
        token("admin-token", "admin-1", "{\"test-client\": {\"roles\": [\"Administrator\"]}}");
        token("self-token", KEYCLOAK_ID.toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("other-token", UUID.randomUUID().toString(), "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("mobile-service-token", "service-account-mobile",
                "{\"test-client\": {\"roles\": [\"MobileService\"]}}");
        token("other-client-token", "someone", "{\"account\": {\"roles\": [\"Administrator\"]}}");
        token("realm-role-token", "someone-else", null);
        token("no-roles-token", "nobody", "{}");
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));

        caretaker = caretaker(ID, KEYCLOAK_ID);
        otherCaretaker = caretaker(OTHER_ID, OTHER_KEYCLOAK_ID);
        when(caretakerService.findByKeycloakId(KEYCLOAK_ID)).thenReturn(caretaker);
        when(caretakerService.findByKeycloakId(OTHER_KEYCLOAK_ID)).thenReturn(otherCaretaker);
        when(caretakerService.findById(any(UUID.class))).thenAnswer(invocation -> {
            final UUID id = invocation.getArgument(0);
            if (ID.equals(id)) {
                return caretaker;
            }
            if (OTHER_ID.equals(id)) {
                return otherCaretaker;
            }
            throw new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
        });
        when(caretakerService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(caretakerService.create(any())).thenReturn(caretaker);
        when(caretakerService.update(any())).thenReturn(caretaker);
        when(caretakerMapper.toDTO(any(Caretaker.class))).thenAnswer(invocation -> {
            final Caretaker c = invocation.getArgument(0);
            return new CaretakerDTO(c.getId(), c.getKeycloakId(), "Test", "Caretaker", "test@example.org", "sl",
                    null, null);
        });
    }

    private static Caretaker caretaker(UUID id, UUID keycloakId) {
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(id);
        when(caretaker.getKeycloakId()).thenReturn(keycloakId);
        return caretaker;
    }

    private void token(String value, String subject, String resourceAccess) throws Exception {
        String claims = "{\"sub\": \"" + subject + "\""
                + (resourceAccess == null
                        ? ", \"realm_access\": {\"roles\": [\"Administrator\"]}"
                        : ", \"resource_access\": " + resourceAccess) + "}";
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

    private ResultActions list(String bearer) throws Exception {
        return perform(get("/api/v1/caretakers"), bearer);
    }

    private ResultActions byId(UUID id, String bearer) throws Exception {
        return perform(get("/api/v1/caretakers/" + id), bearer);
    }

    private ResultActions byKeycloakId(UUID keycloakId, String bearer) throws Exception {
        return perform(get("/api/v1/caretakers/keycloak/" + keycloakId), bearer);
    }

    private ResultActions create(String bearer) throws Exception {
        return perform(post("/api/v1/caretakers").contentType(MediaType.APPLICATION_JSON).content(BODY), bearer);
    }

    private ResultActions update(UUID id, String bearer) throws Exception {
        return perform(put("/api/v1/caretakers/" + id).contentType(MediaType.APPLICATION_JSON).content(BODY),
                bearer);
    }

    @Test
    @DisplayName("An administrator may list, create, read and update any caretaker")
    void administrator_isAllowed() throws Exception {
        list("admin-token").andExpect(status().isOk());
        create("admin-token").andExpect(status().isCreated());
        byId(ID, "admin-token").andExpect(status().isOk());
        byId(OTHER_ID, "admin-token").andExpect(status().isOk());
        byKeycloakId(KEYCLOAK_ID, "admin-token").andExpect(status().isOk());
        update(ID, "admin-token").andExpect(status().isOk());
        update(OTHER_ID, "admin-token").andExpect(status().isOk());

        verify(caretakerService).findAll(any(), any());
        verify(caretakerService).create(any());
        verify(caretakerService, org.mockito.Mockito.times(2)).update(any());
    }

    @Test
    @DisplayName("A caretaker may read and update their own record")
    void caretaker_mayReadAndUpdateTheirOwnRecord() throws Exception {
        byId(ID, "self-token").andExpect(status().isOk());
        byKeycloakId(KEYCLOAK_ID, "self-token").andExpect(status().isOk());
        update(ID, "self-token").andExpect(status().isOk());

        verify(caretakerService).update(any());
    }

    @Test
    @DisplayName("A caretaker may not list or create caretakers")
    void caretaker_isForbiddenToListAndCreate() throws Exception {
        list("self-token").andExpect(status().isForbidden());
        create("self-token").andExpect(status().isForbidden());

        verify(caretakerService, never()).findAll(any(), any());
        verify(caretakerService, never()).create(any());
    }

    @Test
    @DisplayName("A caretaker gets the same not-found response for another caretaker as for an unknown one")
    void caretaker_isNotFoundForAnotherCaretaker() throws Exception {
        final String unknown = byId(UUID.randomUUID(), "self-token").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(byId(OTHER_ID, "self-token").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(unknown)
                .doesNotContain(OTHER_KEYCLOAK_ID.toString());
        assertThat(byKeycloakId(OTHER_KEYCLOAK_ID, "self-token").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(unknown);
        assertThat(update(OTHER_ID, "self-token").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(unknown);

        verify(caretakerService, never()).update(any());
        verifyNoInteractions(caretakerMapper);
    }

    @Test
    @DisplayName("Who the caretaker is comes from the token only, not from the request body")
    void self_isNotTakenFromTheRequest() throws Exception {
        final String body = "{\"id\":\"" + ID + "\",\"keycloakId\":\"" + KEYCLOAK_ID
                + "\",\"firstName\":\"Test\",\"lastName\":\"Caretaker\",\"email\":\"test@example.org\","
                + "\"language\":\"sl\"}";

        perform(put("/api/v1/caretakers/" + OTHER_ID).contentType(MediaType.APPLICATION_JSON).content(body),
                "other-token").andExpect(status().isNotFound());
        perform(put("/api/v1/caretakers/" + OTHER_ID).contentType(MediaType.APPLICATION_JSON).content(body),
                "self-token").andExpect(status().isNotFound());

        verify(caretakerService, never()).update(any());
    }

    @Test
    @DisplayName("Another caretaker, the mobile backend's service account, a role of another client, a realm "
            + "role or no roles may not list or create, and are not found for a record that is not theirs")
    void otherCallers_areDenied() throws Exception {
        for (String bearer : new String[] {"other-token", "mobile-service-token", "other-client-token",
                "realm-role-token", "no-roles-token"}) {
            list(bearer).andExpect(status().isForbidden());
            create(bearer).andExpect(status().isForbidden());
            byId(ID, bearer).andExpect(status().isNotFound());
            byKeycloakId(KEYCLOAK_ID, bearer).andExpect(status().isNotFound());
            update(ID, bearer).andExpect(status().isNotFound());
        }

        verify(caretakerService, never()).findAll(any(), any());
        verify(caretakerService, never()).create(any());
        verify(caretakerService, never()).update(any());
        verifyNoInteractions(caretakerMapper);
    }

    @Test
    @DisplayName("An anonymous caller or an invalid token is unauthorized")
    void anonymousAndInvalid_areUnauthorized() throws Exception {
        for (String bearer : new String[] {null, "invalid"}) {
            list(bearer).andExpect(status().isUnauthorized());
            create(bearer).andExpect(status().isUnauthorized());
            byId(ID, bearer).andExpect(status().isUnauthorized());
            byKeycloakId(KEYCLOAK_ID, bearer).andExpect(status().isUnauthorized());
            update(ID, bearer).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(caretakerService, caretakerMapper);
    }
}
