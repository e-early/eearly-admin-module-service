package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionRepository;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.resource.analysis.AnalysisController;
import si.result.project.eearly.resource.analysis.AnalysisControllerExceptionHandler;

/**
 * The two result endpoints while callback tokens are OFF (no secret): exactly as before, a Keycloak JWT only, and a
 * token in the query string, however well formed, means nothing.
 */
@WebMvcTest(AnalysisController.class)
@ContextConfiguration(classes = {SecurityConfig.class, AnalysisController.class,
        CallbackTokenDisabledSecurityTest.NoSecretConfig.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, AnalysisControllerExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.config.name=callback-token-disabled-security-test",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client"
})
class CallbackTokenDisabledSecurityTest {

    @TestConfiguration
    static class NoSecretConfig {
        @Bean
        CallbackTokenService callbackTokenService(final AlgorithmExecutionRepository repository) {
            return new CallbackTokenService(repository, CallbackTokenTestSupport.properties(""));
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

    private ResultActions postResult(final UUID analysisId, final String query, final String bearer) throws Exception {
        final var request = post("/api/v1/analyses/" + analysisId + "/result" + query)
                .contentType(MediaType.APPLICATION_JSON).content(CallbackTokenTestSupport.body(analysisId));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    @Test
    void tokensAreOff() {
        org.assertj.core.api.Assertions.assertThat(tokens.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("A token that would be valid with a secret is ignored: 401, and the facade is never reached")
    void tokenIsIgnored() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final String secret = CallbackTokenTestSupport.randomSecret();
        final AlgorithmExecution execution = CallbackTokenTestSupport.storeExecution(executions, analysisId);
        final UUID jti = UUID.randomUUID();
        execution.issueCallbackToken(jti, Instant.now().plusSeconds(600));
        final String token = CallbackTokenTestSupport.sign(secret, execution.getId(), jti, Instant.now().plusSeconds(600));

        postResult(analysisId, "?token=" + token, null).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/analyses/results?token=" + token)
                .contentType(MediaType.APPLICATION_JSON).content(CallbackTokenTestSupport.body(analysisId)))
                .andExpect(status().isUnauthorized());

        verify(facade, never()).updateResult(any(), any(), any());
    }

    @Test
    void withoutAnyCredential_isUnauthorized() throws Exception {
        postResult(UUID.randomUUID(), "", null).andExpect(status().isUnauthorized());

        verify(facade, never()).updateResult(any(), any(), any());
    }

    @Test
    @DisplayName("A Keycloak JWT works as before")
    void jwt_works() throws Exception {
        CallbackTokenTestSupport.acceptJwt(jwtDecoder, "good-jwt", "test-client");
        final UUID analysisId = UUID.randomUUID();

        postResult(analysisId, "", "good-jwt").andExpect(status().isOk());

        verify(facade).updateResult(eq(analysisId), any(), isNull());
    }
}
