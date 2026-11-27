package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import si.result.project.eearly.config.CallbackTokenProperties;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionRepository;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.resource.analysis.AnalysisController;
import si.result.project.eearly.resource.analysis.AnalysisControllerExceptionHandler;

/**
 * The two result endpoints while callback tokens are ON (a secret is configured): either a Keycloak JWT or a valid
 * single-use token, nothing else, and nothing that fails ever reaches the facade.
 */
@WebMvcTest(AnalysisController.class)
@ContextConfiguration(classes = {SecurityConfig.class, AnalysisController.class,
        CallbackTokenEndpointSecurityTest.TokenConfig.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, AnalysisControllerExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.config.name=callback-token-security-test",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class CallbackTokenEndpointSecurityTest {

    static final String SECRET = CallbackTokenTestSupport.randomSecret();

    @TestConfiguration
    static class TokenConfig {
        @Bean
        CallbackTokenService callbackTokenService(final AlgorithmExecutionRepository repository) {
            return new CallbackTokenService(repository, CallbackTokenTestSupport.properties(SECRET));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CallbackTokenService tokens;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AnalysisServiceFacade facade;
    @MockitoBean
    private AlgorithmExecutionRepository executions;

    /** The facade of the running service uses the token in its own transaction; this stands in for it. */
    private void facadeConsumesTheToken() {
        when(facade.updateResult(any(), any(), any())).thenAnswer(invocation -> {
            final String token = invocation.getArgument(2);
            if (token != null) {
                tokens.verifyAndConsume(token, invocation.getArgument(0));
            }
            return null;
        });
    }

    private String issuedToken(final UUID analysisId) {
        final AlgorithmExecution execution = CallbackTokenTestSupport.storeExecution(executions, analysisId);
        return tokens.issueToken(execution.getId());
    }

    private ResultActions postResult(final UUID analysisId, final String query, final String bearer) throws Exception {
        final MockHttpServletRequestBuilder request = post("/api/v1/analyses/" + analysisId + "/result" + query)
                .contentType(MediaType.APPLICATION_JSON).content(CallbackTokenTestSupport.body(analysisId));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    private ResultActions postResults(final UUID analysisId, final UUID bodyAnalysisId, final String query)
            throws Exception {
        return mockMvc.perform(post("/api/v1/analyses/results" + query)
                .contentType(MediaType.APPLICATION_JSON).content(CallbackTokenTestSupport.body(bodyAnalysisId)));
    }

    @Test
    @DisplayName("A valid token is accepted once, and the facade is handed the token to use up")
    void validToken_isAcceptedOnce() throws Exception {
        facadeConsumesTheToken();
        final UUID analysisId = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        postResult(analysisId, "?token=" + token, null).andExpect(status().isOk());
        verify(facade).updateResult(eq(analysisId), any(), eq(token));

        postResult(analysisId, "?token=" + token, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CALLBACK_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("The same holds for the endpoint that takes the analysis id from the body")
    void validToken_worksOnTheBodyIdEndpoint() throws Exception {
        facadeConsumesTheToken();
        final UUID analysisId = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        postResults(analysisId, analysisId, "?token=" + token).andExpect(status().isOk());
        postResults(analysisId, analysisId, "?token=" + token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token issued for another analysis is refused, on both endpoints, and is not used up")
    void tokenOfAnotherAnalysis_isRefused() throws Exception {
        facadeConsumesTheToken();
        final UUID analysisId = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        postResult(other, "?token=" + token, null).andExpect(status().isUnauthorized());
        postResults(analysisId, other, "?token=" + token).andExpect(status().isUnauthorized());
        // the rightful use still works
        postResult(analysisId, "?token=" + token, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("An expired token is refused before it reaches the facade")
    void expiredToken_isRefused() throws Exception {
        facadeConsumesTheToken();
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = CallbackTokenTestSupport.storeExecution(executions, analysisId);
        final UUID jti = UUID.randomUUID();
        execution.issueCallbackToken(jti, Instant.now().minusSeconds(60));
        final String token = CallbackTokenTestSupport.sign(SECRET, execution.getId(), jti, Instant.now().minusSeconds(60));

        postResult(analysisId, "?token=" + token, null).andExpect(status().isUnauthorized());

        verify(facade, never()).updateResult(any(), any(), any());
    }

    @Test
    @DisplayName("A forged token (another secret) is refused before it reaches the facade")
    void forgedToken_isRefused() throws Exception {
        facadeConsumesTheToken();
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = CallbackTokenTestSupport.storeExecution(executions, analysisId);
        final UUID jti = UUID.randomUUID();
        execution.issueCallbackToken(jti, Instant.now().plusSeconds(600));
        final String forged = CallbackTokenTestSupport.sign(
                CallbackTokenTestSupport.randomSecret(), execution.getId(), jti, Instant.now().plusSeconds(600));

        postResult(analysisId, "?token=" + forged, null).andExpect(status().isUnauthorized());

        verify(facade, never()).updateResult(any(), any(), any());
    }

    @Test
    @DisplayName("No token, an empty token, garbage and a repeated token parameter are all 401")
    void missingOrMalformedToken_isUnauthorized() throws Exception {
        facadeConsumesTheToken();
        final UUID analysisId = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        postResult(analysisId, "", null).andExpect(status().isUnauthorized());
        postResult(analysisId, "?token=", null).andExpect(status().isUnauthorized());
        postResult(analysisId, "?token=garbage", null).andExpect(status().isUnauthorized());
        postResult(analysisId, "?token=" + token + "&token=" + token, null).andExpect(status().isUnauthorized());
        postResult(analysisId, "?other=" + token, null).andExpect(status().isUnauthorized());

        verify(facade, never()).updateResult(any(), any(), any());
    }

    @Test
    @DisplayName("A Keycloak JWT still works, and then no token is passed on")
    void jwt_stillWorks() throws Exception {
        CallbackTokenTestSupport.acceptJwt(jwtDecoder, "good-jwt", "test-client");
        final UUID analysisId = UUID.randomUUID();

        postResult(analysisId, "", "good-jwt").andExpect(status().isOk());

        verify(facade).updateResult(eq(analysisId), any(), isNull());
    }

    @Test
    @DisplayName("With a JWT the token parameter is ignored: it is not used up and not required to be valid")
    void jwtWithATokenParameter_ignoresTheToken() throws Exception {
        CallbackTokenTestSupport.acceptJwt(jwtDecoder, "good-jwt", "test-client");
        final UUID analysisId = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        postResult(analysisId, "?token=" + token, "good-jwt").andExpect(status().isOk());

        verify(facade).updateResult(eq(analysisId), any(), isNull());
    }

    @Test
    @DisplayName("An invalid JWT is 401 even next to a valid token: the JWT way is taken, as without tokens")
    void invalidJwt_isUnauthorized() throws Exception {
        when(jwtDecoder.decode("bad-jwt")).thenThrow(new BadJwtException("invalid"));
        final UUID analysisId = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        postResult(analysisId, "?token=" + token, "bad-jwt").andExpect(status().isUnauthorized());

        verify(facade, never()).updateResult(any(), any(), any());
    }

    @Test
    @DisplayName("The token opens these two endpoints only, not the rest of the API")
    void tokenDoesNotOpenOtherEndpoints() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final String token = issuedToken(analysisId);

        mockMvc.perform(post("/api/v1/analyses?token=" + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/analyses/" + analysisId + "/result/extra?token=" + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/analyses/" + analysisId + "/result?token=" + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/analyses/" + analysisId + "/result?token=" + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(facade, times(0)).updateResult(any(), any(), any());
    }

    @Test
    @DisplayName("An anonymous call is 401")
    void anonymous_isUnauthorized() throws Exception {
        postResult(UUID.randomUUID(), "", null).andExpect(status().isUnauthorized());
    }
}
