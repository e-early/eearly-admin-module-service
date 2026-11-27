package si.result.project.eearly.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import si.result.logging.spring.boot.config.LoggingConfig;
import si.result.logging.spring.boot.config.ServicePropertiesConfig;
import si.result.logging.spring.boot.config.properties.LoggingConfigurationProperties;
import si.result.project.eearly.config.logging.RequestLogMaskingConfig;
import si.result.project.eearly.config.security.SecurityConfig;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionRepository;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.resource.analysis.AnalysisController;
import si.result.project.eearly.resource.analysis.AnalysisControllerExceptionHandler;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;

/**
 * The result callback URL carries the single-use token in its query string, and the request logging of the service
 * writes the query string. With the request logging switched on as in application.yaml, the token must appear
 * nowhere in the log: not for a call that works, not for a replay, not for a refused one.
 */
@WebMvcTest(AnalysisController.class)
@ContextConfiguration(classes = {SecurityConfig.class, AnalysisController.class, CallbackTokenLoggingTest.Cfg.class,
        LoggingConfig.class, ServicePropertiesConfig.class, LoggingConfigurationProperties.class,
        SseLoggingBypassConfig.class, RequestLogMaskingConfig.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, AnalysisControllerExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.config.name=callback-token-logging-test",
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client",
        "logging.loggers.http.enabled=true",
        "logging.loggers.http.log-response=true",
        "logging.loggers.http.log-authorization-header=false",
        "logging.loggers.method.enabled=true"
})
class CallbackTokenLoggingTest {

    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();

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

        @Bean
        CallbackTokenService callbackTokenService(final AlgorithmExecutionRepository repository) {
            final CallbackTokenProperties properties = new CallbackTokenProperties();
            properties.setTokenSecret(SECRET);
            return new CallbackTokenService(repository, properties);
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

    @Test
    @DisplayName("The token is in no log line, for a call that works, a replay and a forged token, and the query is logged masked")
    void theLogHoldsNoToken(final CapturedOutput output) throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final Analysis analysis = org.mockito.Mockito.mock(Analysis.class);
        when(analysis.getId()).thenReturn(analysisId);
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(UUID.randomUUID()).analysis(analysis)
                .executionStatus(AlgorithmExecutionStatus.RUNNING).build();
        when(executions.findById(execution.getId())).thenReturn(java.util.Optional.of(execution));
        when(executions.findByIdForUpdate(execution.getId())).thenReturn(java.util.Optional.of(execution));
        when(executions.save(any(AlgorithmExecution.class))).thenAnswer(i -> i.getArgument(0));
        when(facade.updateResult(any(), any(), any())).thenAnswer(invocation -> {
            final String token = invocation.getArgument(2);
            if (token != null) {
                tokens.verifyAndConsume(token, invocation.getArgument(0));
            }
            return null;
        });
        final String token = tokens.issueToken(execution.getId());
        final String forged = token.substring(0, token.indexOf('.') + 1) + "A".repeat(43);
        final String body = "{\"correlationId\":null,\"analysisId\":\"" + analysisId
                + "\",\"state\":\"COMPLETED\",\"detected\":true,\"name\":\"m\",\"detections\":[]}";

        mockMvc.perform(post("/api/v1/analyses/" + analysisId + "/result?token=" + token + "&x=1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/analyses/" + analysisId + "/result?token=" + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/analyses/" + analysisId + "/result?token=" + forged)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/analyses/results?token=" + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());

        // The request logging is on: the calls are logged, with the query string, masked ...
        assertThat(output.getAll()).contains("/api/v1/analyses/" + analysisId + "/result");
        assertThat(output.getAll()).contains("token=***");
        // ... and neither the token, nor its payload part, nor its signature part, is in the log.
        final String payload = token.substring(0, token.indexOf('.'));
        final String signature = token.substring(token.indexOf('.') + 1);
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(payload).doesNotContain(signature);
        assertThat(output.getAll()).doesNotContain(forged);
    }
}
