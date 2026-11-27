package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import si.result.project.eearly.dto.healthcondition.HealthConditionDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionMinimalDTO;
import si.result.project.eearly.facade.healthcondition.HealthConditionServiceFacade;
import si.result.project.eearly.resource.healthcondition.HealthConditionController;

/**
 * Who may call the health condition endpoints: reading is open to any authenticated caller, creating
 * requires the Administrator client role of the configured client.
 */
@WebMvcTest(HealthConditionController.class)
@ContextConfiguration(classes = {SecurityConfig.class, HealthConditionController.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.config.name=health-condition-endpoint-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class HealthConditionEndpointSecurityTest {

    private static final UUID ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    private static final String BODY = "{\"name\":\"Test Condition\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private HealthConditionServiceFacade facade;

    @BeforeEach
    void tokens() throws Exception {
        token("admin-token", "admin-1",
                "{\"test-client\": {\"roles\": [\"Administrator\"]}}");
        token("clinician-token", "caretaker-1",
                "{\"test-client\": {\"roles\": [\"Clinician\"]}}");
        token("mobile-service-token", "service-account-mobile",
                "{\"test-client\": {\"roles\": [\"MobileService\"]}}");
        token("other-client-token", "someone",
                "{\"account\": {\"roles\": [\"Administrator\"]}}");
        token("realm-role-token", "someone-else", null);
        token("no-roles-token", "nobody", "{}");
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));

        when(facade.getById(ID)).thenReturn(new HealthConditionDTO(ID, "Test Condition"));
        when(facade.create(any())).thenReturn(new HealthConditionMinimalDTO(ID));
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
        return perform(get("/api/v1/healthconditions"), bearer);
    }

    private ResultActions byId(String bearer) throws Exception {
        return perform(get("/api/v1/healthconditions/" + ID), bearer);
    }

    private ResultActions create(String bearer) throws Exception {
        return perform(post("/api/v1/healthconditions").contentType(MediaType.APPLICATION_JSON).content(BODY), bearer);
    }

    @Test
    @DisplayName("An administrator may list, read and create health conditions")
    void administrator_isAllowed() throws Exception {
        list("admin-token").andExpect(status().isOk());
        byId("admin-token").andExpect(status().isOk());
        create("admin-token").andExpect(status().isCreated());

        verify(facade).getById(ID);
        verify(facade).create(any());
    }

    @Test
    @DisplayName("Any authenticated caller may read health conditions")
    void authenticatedCallers_mayRead() throws Exception {
        for (String bearer : new String[] {"clinician-token", "mobile-service-token", "other-client-token",
                "realm-role-token", "no-roles-token"}) {
            list(bearer).andExpect(status().isOk());
            byId(bearer).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("A caretaker without the Administrator role may not create")
    void clinician_isForbiddenToCreate() throws Exception {
        create("clinician-token").andExpect(status().isForbidden());

        verify(facade, never()).create(any());
    }

    @Test
    @DisplayName("The mobile backend's service account may not create")
    void mobileService_isForbiddenToCreate() throws Exception {
        create("mobile-service-token").andExpect(status().isForbidden());

        verify(facade, never()).create(any());
    }

    @Test
    @DisplayName("An Administrator role of another client, a realm role or no roles may not create")
    void foreignOrMissingRoles_areForbiddenToCreate() throws Exception {
        for (String bearer : new String[] {"other-client-token", "realm-role-token", "no-roles-token"}) {
            create(bearer).andExpect(status().isForbidden());
        }

        verify(facade, never()).create(any());
    }

    @Test
    @DisplayName("An anonymous caller or an invalid token is unauthorized")
    void anonymousAndInvalid_areUnauthorized() throws Exception {
        list(null).andExpect(status().isUnauthorized());
        byId(null).andExpect(status().isUnauthorized());
        create(null).andExpect(status().isUnauthorized());
        list("invalid").andExpect(status().isUnauthorized());
        create("invalid").andExpect(status().isUnauthorized());

        verifyNoInteractions(facade);
    }
}
