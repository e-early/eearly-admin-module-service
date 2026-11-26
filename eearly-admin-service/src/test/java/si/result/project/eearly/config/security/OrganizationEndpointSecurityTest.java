package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import si.result.project.eearly.dto.organization.OrganizationDTO;
import si.result.project.eearly.dto.organization.OrganizationUpsertDTO;
import si.result.project.eearly.facade.organization.OrganizationServiceFacade;
import si.result.project.eearly.resource.organization.OrganizationController;

/**
 * Who may call the organization endpoints: only a token carrying the Administrator client role of
 * the configured client.
 */
@WebMvcTest(OrganizationController.class)
@ContextConfiguration(classes = {SecurityConfig.class, OrganizationController.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.config.name=organization-endpoint-security-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class OrganizationEndpointSecurityTest {

    private static final UUID ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    private static final String BODY = "{\"name\":\"Test Organization\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrganizationServiceFacade facade;

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

        when(facade.getById(ID)).thenReturn(new OrganizationDTO(ID, "Test Organization"));
        when(facade.create(any())).thenReturn(new OrganizationUpsertDTO(ID, "Test Organization"));
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
        return perform(get("/api/v1/organizations"), bearer);
    }

    private ResultActions byId(String bearer) throws Exception {
        return perform(get("/api/v1/organizations/" + ID), bearer);
    }

    private ResultActions create(String bearer) throws Exception {
        return perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON).content(BODY), bearer);
    }

    @Test
    @DisplayName("An administrator may list, read and create organizations")
    void administrator_isAllowed() throws Exception {
        list("admin-token").andExpect(status().isOk());
        byId("admin-token").andExpect(status().isOk());
        create("admin-token").andExpect(status().isCreated());

        verify(facade).getById(ID);
        verify(facade).create(any());
    }

    @Test
    @DisplayName("A caretaker without the Administrator role is forbidden")
    void clinician_isForbidden() throws Exception {
        list("clinician-token").andExpect(status().isForbidden());
        byId("clinician-token").andExpect(status().isForbidden());
        create("clinician-token").andExpect(status().isForbidden());

        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("The mobile backend's service account is forbidden")
    void mobileService_isForbidden() throws Exception {
        list("mobile-service-token").andExpect(status().isForbidden());
        create("mobile-service-token").andExpect(status().isForbidden());

        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("An Administrator role of another client, a realm role or no roles are forbidden")
    void foreignOrMissingRoles_areForbidden() throws Exception {
        for (String bearer : new String[] {"other-client-token", "realm-role-token", "no-roles-token"}) {
            list(bearer).andExpect(status().isForbidden());
            byId(bearer).andExpect(status().isForbidden());
            create(bearer).andExpect(status().isForbidden());
        }

        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("Every method under the organization path requires the Administrator role")
    void otherMethodsAreForbiddenForNonAdministrators() throws Exception {
        perform(put("/api/v1/organizations/" + ID).contentType(MediaType.APPLICATION_JSON).content(BODY),
                "clinician-token").andExpect(status().isForbidden());
        perform(delete("/api/v1/organizations/" + ID), "clinician-token").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An anonymous caller or an invalid token is unauthorized")
    void anonymousAndInvalid_areUnauthorized() throws Exception {
        list(null).andExpect(status().isUnauthorized());
        byId(null).andExpect(status().isUnauthorized());
        create(null).andExpect(status().isUnauthorized());
        list("invalid").andExpect(status().isUnauthorized());
        create("invalid").andExpect(status().isUnauthorized());

        verify(facade, never()).create(any());
        verify(facade, never()).getById(any());
    }
}
