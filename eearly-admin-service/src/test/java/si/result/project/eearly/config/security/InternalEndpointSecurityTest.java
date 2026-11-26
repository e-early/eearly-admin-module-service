package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import si.result.project.eearly.facade.patient.InternalPatientServiceFacade;
import si.result.project.eearly.resource.patient.InternalPatientController;

/**
 * Who may call the internal endpoint the mobile backend uses: only a token carrying the
 * MobileService client role of the configured client.
 */
@WebMvcTest(InternalPatientController.class)
@ContextConfiguration(classes = {SecurityConfig.class, InternalPatientController.class})
@Import(si.result.spring.boot.bricks.advice.ErrorResponseHandler.class)
@TestPropertySource(properties = {
        "spring.config.name=internal-endpoint-security-test",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class InternalEndpointSecurityTest {

    private static final UUID KEYCLOAK_ID = UUID.fromString("50287036-1b84-4e2e-831f-91ab621e52d9");
    private static final String URL = "/api/v1/internal/patients/" + KEYCLOAK_ID + "/language";
    private static final String BODY = "{\"language\":\"en\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private InternalPatientServiceFacade facade;

    @BeforeEach
    void tokens() throws Exception {
        token("mobile-service-token", "service-account-mobile",
                "{\"test-client\": {\"roles\": [\"MobileService\"]}}");
        token("caretaker-token", "caretaker-1",
                "{\"test-client\": {\"roles\": [\"Clinician\", \"Administrator\"]}}");
        token("patient-token", "patient-1", "{\"account\": {\"roles\": [\"MobileService\"]}}");
        token("realm-role-token", "someone", null);
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
    }

    private void token(String value, String subject, String resourceAccess) throws Exception {
        String claims = "{\"sub\": \"" + subject + "\""
                + (resourceAccess == null
                        ? ", \"realm_access\": {\"roles\": [\"MobileService\"]}"
                        : ", \"resource_access\": " + resourceAccess) + "}";
        var parsed = JWTClaimsSet.parse(claims).getClaims();
        when(jwtDecoder.decode(value)).thenReturn(
                Jwt.withTokenValue(value).header("alg", "RS256").claims(c -> c.putAll(parsed)).build());
    }

    private org.springframework.test.web.servlet.ResultActions call(String bearer) throws Exception {
        var request = put(URL).contentType(MediaType.APPLICATION_JSON).content(BODY);
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("The mobile backend's service account may change a patient's language")
    void mobileService_isAllowed() throws Exception {
        call("mobile-service-token").andExpect(status().isNoContent());

        verify(facade).changeLanguage(eq(KEYCLOAK_ID), any());
    }

    @Test
    @DisplayName("A caretaker, even an administrator, is forbidden")
    void caretaker_isForbidden() throws Exception {
        call("caretaker-token").andExpect(status().isForbidden());

        verify(facade, never()).changeLanguage(any(), any());
    }

    @Test
    @DisplayName("A role of another client is forbidden")
    void otherClientRole_isForbidden() throws Exception {
        call("patient-token").andExpect(status().isForbidden());

        verify(facade, never()).changeLanguage(any(), any());
    }

    @Test
    @DisplayName("A realm role of the same name is forbidden")
    void realmRole_isForbidden() throws Exception {
        call("realm-role-token").andExpect(status().isForbidden());

        verify(facade, never()).changeLanguage(any(), any());
    }

    @Test
    @DisplayName("An anonymous caller or an invalid token is unauthorized")
    void anonymousAndInvalid_areUnauthorized() throws Exception {
        call(null).andExpect(status().isUnauthorized());
        call("invalid").andExpect(status().isUnauthorized());

        verify(facade, never()).changeLanguage(any(), any());
    }
}
