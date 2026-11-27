package si.result.project.eearly.facade.algorithmexecution;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.config.AlgorithmCredentialsProperties;
import si.result.project.eearly.config.algorithm.AlgorithmApiProperties;
import si.result.project.eearly.config.algorithm.AlgorithmHttpClient;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithm.AlgorithmCredentialCipher;
import si.result.project.eearly.port.algorithm.AlgorithmCredentialService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmDataPreparationService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionPayloadBuilder;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.port.algorithmexecution.ScheduledMeasurementWindowStrategy;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.project.eearly.util.JsonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpAlgorithmExecutionAdapterTest {

    private static final byte[] LOOPBACK = {127, 0, 0, 1};

    @Mock
    private AlgorithmExecutionService algorithmExecutionService;

    @Mock
    private AlgorithmDataPreparationService algorithmDataPreparationService;

    @Mock
    private CallbackTokenService callbackTokenService;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private AnalysisServiceFacade analysisServiceFacade;

    private HttpServer server;
    private StubRemoteServer stubServer;
    private HttpAlgorithmExecutionAdapter adapter;
    private AlgorithmApiProperties properties;
    private AlgorithmCredentialCipher cipher;

    @BeforeEach
    void setUp() throws IOException {
        stubServer = new StubRemoteServer();
        // Bind to the IPv4 loopback address and call exactly that address. A wildcard bind plus the host name
        // "localhost" can reach a different listener that owns the same port on another loopback address
        // (seen as an "Invalid status line" from a foreign service), which made these tests flaky.
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(LOOPBACK), 0), 0);
        server.createContext("/healthz", stubServer::handleHealthz);
        server.createContext("/run", stubServer::handleRun);
        server.createContext("/tasks", stubServer::handleTasks);
        server.createContext("/results", stubServer::handleResults);
        server.start();

        properties = new AlgorithmApiProperties();
        properties.setDefaultTimeoutSeconds(5);
        properties.setAsyncPollIntervalMs(100);
        properties.setVerifySsl(true);

        // Callback tokens are off (the mock answers isEnabled() with false) unless a test switches them on.
        final byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        final AlgorithmCredentialsProperties credentialsProperties = new AlgorithmCredentialsProperties();
        credentialsProperties.setEncryptionKey(Base64.getEncoder().encodeToString(key));
        cipher = new AlgorithmCredentialCipher(credentialsProperties);
        adapter = new HttpAlgorithmExecutionAdapter(
                algorithmExecutionService,
                new AlgorithmExecutionPayloadBuilder(algorithmDataPreparationService, callbackTokenService,
                        ScheduledMeasurementWindowStrategy.NONE, "http://localhost:9000"),
                new AlgorithmCredentialService(cipher),
                analysisService,
                analysisServiceFacade,
                properties,
                new AlgorithmHttpClient(properties)
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void run_shouldCompleteExecution_whenHealthAndRunSucceed() {
        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        assertEquals(1, stubServer.healthzCalls.get());
        assertEquals(1, stubServer.runCalls.get());
        assertEquals("{\"value\":42}", stubServer.lastRunRequestBody.get());
        verify(algorithmExecutionService).updateStatus(execution.getId(),
                AlgorithmExecutionStatus.SUBMITTED);
        verify(algorithmExecutionService).updateStatus(execution.getId(),
                AlgorithmExecutionStatus.RUNNING);
        verify(algorithmExecutionService).markCompleted(execution.getId(),
                "{\"prediction\":\"ok\"}");
        verify(algorithmExecutionService, never()).markFailed(execution.getId(), null);
    }

    @Test
    void run_shouldSkipHealthCheck_whenAlgorithmHasNoHealthEndpoint() {
        final AlgorithmExecution execution = createExecutionWithoutHealthEndpoint();
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        assertEquals(0, stubServer.healthzCalls.get());
        assertEquals(1, stubServer.runCalls.get());
        verify(algorithmExecutionService).markCompleted(execution.getId(), "{\"prediction\":\"ok\"}");
    }

    @Test
    void run_shouldPreparePayloadFromTypedAnalysisSelections() {
        final UUID analysisId = UUID.randomUUID();
        final UUID measurementId = UUID.randomUUID();
        final AlgorithmExecution execution = createAnalysisExecution(
                analysisId,
                """
                [
                  {
                    "startTimestamp": "2026-02-12T07:00:00Z",
                    "endTimestamp": "2026-02-12T07:01:20Z",
                    "measurementIds": ["%s"]
                  }
                ]
                """.formatted(measurementId)
        );
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);
        when(analysisService.updateState(analysisId, AnalysisState.IN_PROGRESS)).thenReturn(mock(Analysis.class));
        when(analysisService.updateState(analysisId, AnalysisState.COMPLETED)).thenReturn(mock(Analysis.class));

        when(algorithmDataPreparationService.prepareInputData(
                eq(execution.getPatient()),
                eq(execution.getAlgorithm()),
                eq(List.of(new AnalysisSelection(
                        OffsetDateTime.parse("2026-02-12T07:00:00Z"),
                        OffsetDateTime.parse("2026-02-12T07:01:20Z"),
                        List.of(measurementId)
                ))),
                eq(analysisId),
                eq(execution.getAlgorithm().getRunnerConfig()),
                isNull()
        )).thenReturn("{\"prepared\":true}");

        adapter.run(execution);

        assertEquals("{\"prepared\":true}", stubServer.lastRunRequestBody.get());
        verify(analysisService).updateState(analysisId, AnalysisState.IN_PROGRESS);
        verify(analysisService).updateState(analysisId, AnalysisState.COMPLETED);
    }

    @Test
    void run_shouldConvertSynchronousAlgorithmResultIntoAnalysisResultData() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = createAnalysisExecution(analysisId, "[]");
        stubServer.runResponseBody = """
                {
                  "results": [
                    {
                      "analysisId": "%s",
                      "modelName": "apnea_model",
                      "model_threshold": 0.5,
                      "probabilities": [
                        {
                          "probability": 0.148921,
                          "probStartTime": "2026-05-04T12:55:15+00:00",
                          "probEndTime": "2026-05-04T12:55:29+00:00"
                        },
                        {
                          "probability": 0.155928,
                          "probStartTime": "2026-05-04T12:55:45+00:00",
                          "probEndTime": "2026-05-04T12:55:59+00:00"
                        }
                      ]
                    }
                  ]
                }
                """.formatted(analysisId);

        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);
        when(analysisService.updateState(analysisId, AnalysisState.IN_PROGRESS)).thenReturn(mock(Analysis.class));
        adapter.run(execution);

        verify(analysisServiceFacade).persistAlgorithmPredictions(
                eq(analysisId),
                eq(true),
                anyList(),
                eq(0.5)
        );
    }

    @Test
    void run_shouldParseModelThresholdFromBentoMlCamelCaseResponse() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = createAnalysisExecution(analysisId, "[]");
        stubServer.runResponseBody = """
                {
                  "results": [
                    {
                      "analysisId": "%s",
                      "modelName": "apnea_model",
                      "modelVersion": "1",
                      "modelThreshold": 0.25,
                      "probabilities": [
                        {
                          "probability": 0.074137,
                          "probStartTime": "2026-02-12T08:00:00+01:00",
                          "probEndTime": "2026-02-12T08:00:07+01:00"
                        }
                      ]
                    }
                  ]
                }
                """.formatted(analysisId);

        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);
        when(analysisService.updateState(analysisId, AnalysisState.IN_PROGRESS)).thenReturn(mock(Analysis.class));
        adapter.run(execution);

        verify(analysisServiceFacade).persistAlgorithmPredictions(
                eq(analysisId),
                eq(true),
                anyList(),
                eq(0.25)
        );
    }

    @Test
    void cancel_shouldCallDeleteTask_whenExternalRequestIdPresent() {
        final AlgorithmExecution execution = createExecution("task-123", AlgorithmExecutionStatus.SUBMITTED);

        adapter.cancel(execution);

        assertEquals(1, stubServer.cancelCalls.get());
        assertEquals("/tasks/task-123", stubServer.lastCancelPath.get());
        verify(algorithmExecutionService).markCancelled(execution.getId());
    }

    @Test
    void run_shouldFailExecution_whenHealthCheckFails() {
        stubServer.healthzStatusCode = 503;
        stubServer.healthzResponseBody = "{\"status\":\"down\"}";

        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        verify(algorithmExecutionService).updateStatus(execution.getId(),
                AlgorithmExecutionStatus.SUBMITTED);
        verify(algorithmExecutionService).markFailed(
                eq(execution.getId()),
                org.mockito.ArgumentMatchers.contains("Remote health check failed"));
        assertEquals(0, stubServer.runCalls.get());
    }

    @Test
    void run_shouldStoreTaskIdAndPollUntilCompleted_whenAsyncResponseReturned() {
        stubServer.runResponseBody = "{\"task_id\":\"task-42\"}";
        stubServer.resultsResponseBody = "{\"status\":\"COMPLETED\",\"result\":{\"prediction\":\"ok-async\"}}";

        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        verify(algorithmExecutionService).markRemoteTaskStarted(
                execution.getId(),
                "task-42"
        );
        verify(algorithmExecutionService).markCompleted(execution.getId(), "{\"prediction\":\"ok-async\"}");
        assertEquals(1, stubServer.resultsCalls.get());
        assertEquals("/results/task-42", stubServer.lastResultsPath.get());
    }

    @Test
    void run_shouldConvertAsyncAlgorithmResultIntoAnalysisResultData() throws Exception {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = createAnalysisExecution(analysisId, "[]");
        stubServer.runResponseBody = "{\"task_id\":\"task-42\"}";
        stubServer.resultsResponseBody = """
                {
                  "status": "COMPLETED",
                  "result": {
                    "results": [
                      {
                        "analysisId": "%s",
                        "modelName": "apnea_model",
                        "model_threshold": 0.42,
                        "probabilities": [
                          {
                            "probability": 0.91,
                            "probStartTime": "2026-05-04T13:00:00+00:00",
                            "probEndTime": "2026-05-04T13:00:14+00:00"
                          }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(analysisId);

        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);
        when(analysisService.updateState(analysisId, AnalysisState.IN_PROGRESS)).thenReturn(mock(Analysis.class));
        adapter.run(execution);

        verify(analysisServiceFacade).persistAlgorithmPredictions(
                eq(analysisId),
                eq(true),
                anyList(),
                eq(0.42)
        );
        verify(algorithmExecutionService).markCompleted(
                execution.getId(),
                "{\"results\":[{\"analysisId\":\"%s\",\"modelName\":\"apnea_model\",\"model_threshold\":0.42,\"probabilities\":[{\"probability\":0.91,\"probStartTime\":\"2026-05-04T13:00:00+00:00\",\"probEndTime\":\"2026-05-04T13:00:14+00:00\"}]}]}".formatted(analysisId)
        );
    }

    @Test
    void run_shouldUseBasicAuth_whenConfigured() {
        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        properties.getBasicAuth().setUsername("omop-user");
        properties.getBasicAuth().setPassword("secret");

        adapter.run(execution);

        assertEquals("Basic " + Base64.getEncoder()
                        .encodeToString("omop-user:secret".getBytes(StandardCharsets.UTF_8)),
                stubServer.lastAuthorizationHeader.get());
    }

    @Test
    void run_shouldUpdateAnalysisToError_whenHealthCheckFailsForAnalysisExecution() {
        stubServer.healthzStatusCode = 503;
        stubServer.healthzResponseBody = "{\"status\":\"down\"}";
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = createAnalysisExecution(analysisId, "[]");

        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);
        when(analysisService.updateState(analysisId, AnalysisState.IN_PROGRESS)).thenReturn(mock(Analysis.class));
        when(analysisService.updateState(analysisId, AnalysisState.ERROR)).thenReturn(mock(Analysis.class));

        adapter.run(execution);

        verify(analysisService).updateState(analysisId, AnalysisState.IN_PROGRESS);
        verify(analysisService).updateState(analysisId, AnalysisState.ERROR);
    }

    @Test
    void run_shouldFailExecution_whenAsyncPollingReturnsError() {
        stubServer.runResponseBody = "{\"task_id\":\"task-99\"}";
        stubServer.resultsResponseBody = "{\"status\":\"FAILED\",\"error\":\"worker failed\"}";

        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        verify(algorithmExecutionService).markRemoteTaskStarted(execution.getId(), "task-99");
        verify(algorithmExecutionService).markFailed(execution.getId(), "worker failed");
    }

    private AlgorithmExecution createExecutionWithCredentials(final String username, final String storedPassword,
                                                              final AlgorithmExecutionStatus status,
                                                              final String externalRequestId) {
        return AlgorithmExecution.builder()
                .id(UUID.randomUUID())
                .algorithm(Algorithm.builder()
                        .id(UUID.randomUUID())
                        .name("Test algorithm")
                        .serviceEndpoint(baseUrl())
                        .runEndpoint("/run")
                        .cancelEndpoint("/tasks/{taskId}")
                        .healthCheckEndpoint("/healthz")
                        .authUsername(username)
                        .authPassword(storedPassword)
                        .build())
                .triggerType(AlgorithmExecutionTriggerType.MANUAL)
                .executionStatus(status)
                .inputParameters("{\"value\":42}")
                .externalRequestId(externalRequestId)
                .build();
    }

    private static String basic(final String username, final String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void run_shouldLeaveExecutionRunning_whenResponseIsAcceptedWithNoTaskId_andCallbackTokensAreEnabled() {
        when(callbackTokenService.isEnabled()).thenReturn(true);
        stubServer.runStatusCode = 202;
        stubServer.runResponseBody = "";

        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        verify(algorithmExecutionService).updateStatus(execution.getId(), AlgorithmExecutionStatus.SUBMITTED);
        verify(algorithmExecutionService).updateStatus(execution.getId(), AlgorithmExecutionStatus.RUNNING);
        verify(algorithmExecutionService, never()).markRemoteTaskStarted(any(), any());
        verify(algorithmExecutionService, never()).markCompleted(any(), any());
        verify(algorithmExecutionService, never()).markFailed(any(), any());
        assertEquals(0, stubServer.resultsCalls.get());
    }

    @Test
    void run_shouldAlsoLeaveExecutionRunning_whenAcceptedWithoutCallbackTokens() {
        // Callback tokens are off: the provider got no callback URL, which is logged, but the execution is kept
        // as it was (for a result posted with a JWT, or for the stuck-execution sweep).
        stubServer.runStatusCode = 202;
        stubServer.runResponseBody = "";

        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        verify(algorithmExecutionService).updateStatus(execution.getId(), AlgorithmExecutionStatus.RUNNING);
        verify(algorithmExecutionService, never()).markCompleted(any(), any());
        verify(algorithmExecutionService, never()).markFailed(any(), any());
    }

    @Test
    void run_shouldPollForTheResult_whenAcceptedWithATaskId() {
        stubServer.runStatusCode = 202;
        stubServer.runResponseBody = "{\"task_id\":\"task-42\"}";
        stubServer.resultsResponseBody = "{\"status\":\"COMPLETED\",\"result\":{\"prediction\":\"ok-async\"}}";

        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        verify(algorithmExecutionService).markRemoteTaskStarted(execution.getId(), "task-42");
        verify(algorithmExecutionService).markCompleted(execution.getId(), "{\"prediction\":\"ok-async\"}");
    }

    @Test
    void run_shouldFailTheExecutionAndRestoreTheInterruptFlag_whenTheThreadIsInterrupted() throws Exception {
        stubServer.runDelayMs = 3000;
        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);
        final AtomicBoolean flagAfterRun = new AtomicBoolean();

        final Thread worker = new Thread(() -> {
            adapter.run(execution);
            flagAfterRun.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        final long deadline = System.currentTimeMillis() + 5000;
        while (stubServer.runCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, stubServer.runCalls.get());

        worker.interrupt();
        worker.join(5000);

        assertFalse(worker.isAlive());
        assertTrue(flagAfterRun.get(), "the interrupt flag must be restored for the owner of the thread");
        verify(algorithmExecutionService).markFailed(eq(execution.getId()), any());
    }

    @Test
    void run_shouldSendTheAlgorithmsOwnCredentials_notTheGlobalOnes_whenTheAlgorithmHasThem() {
        properties.getBasicAuth().setUsername("global-user");
        properties.getBasicAuth().setPassword("global-secret");
        properties.setApiKey("global-api-key");
        final AlgorithmExecution execution = createExecutionWithCredentials(
                "algorithm-user", cipher.encrypt("algorithm-secret"), AlgorithmExecutionStatus.PENDING, null);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        assertEquals(basic("algorithm-user", "algorithm-secret"), stubServer.lastAuthorizationHeader.get());
        assertEquals(1, stubServer.runCalls.get());
        verify(algorithmExecutionService).markCompleted(eq(execution.getId()), any());
    }

    @Test
    void run_shouldOmitAuthorization_whenNeitherTheAlgorithmNorTheClientHasCredentials() {
        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        assertNull(stubServer.lastAuthorizationHeader.get());
    }

    @Test
    void run_shouldFallBackToTheGlobalBasicAuth_whenTheAlgorithmHasNoCredentials() {
        properties.getBasicAuth().setUsername("global-user");
        properties.getBasicAuth().setPassword("global-secret");
        final AlgorithmExecution execution = createExecution(null, AlgorithmExecutionStatus.PENDING);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        assertEquals(basic("global-user", "global-secret"), stubServer.lastAuthorizationHeader.get());
    }

    @Test
    void run_shouldFailWithAClearMessageAndSendNothing_whenTheStoredPasswordCannotBeDecrypted() {
        properties.getBasicAuth().setUsername("global-user");
        properties.getBasicAuth().setPassword("global-secret");
        // Encrypted under another key, as after the key of the deployment was changed.
        final byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        final AlgorithmCredentialsProperties otherProperties = new AlgorithmCredentialsProperties();
        otherProperties.setEncryptionKey(Base64.getEncoder().encodeToString(otherKey));
        final String foreign = new AlgorithmCredentialCipher(otherProperties).encrypt("algorithm-secret");
        final AlgorithmExecution execution = createExecutionWithCredentials(
                "algorithm-user", foreign, AlgorithmExecutionStatus.PENDING, null);
        when(algorithmExecutionService.findInitializedById(execution.getId())).thenReturn(execution);

        adapter.run(execution);

        // Neither the algorithm's login nor, above all, the global credentials went to this endpoint.
        assertEquals(0, stubServer.healthzCalls.get());
        assertEquals(0, stubServer.runCalls.get());
        assertNull(stubServer.lastAuthorizationHeader.get());
        final ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(algorithmExecutionService).markFailed(eq(execution.getId()), message.capture());
        assertTrue(message.getValue().contains("encryption key"));
        assertFalse(message.getValue().contains("algorithm-secret"));
    }

    @Test
    void cancel_shouldUseTheAlgorithmsOwnCredentials() {
        final AlgorithmExecution execution = createExecutionWithCredentials(
                "algorithm-user", cipher.encrypt("algorithm-secret"), AlgorithmExecutionStatus.SUBMITTED, "task-123");

        adapter.cancel(execution);

        assertEquals(basic("algorithm-user", "algorithm-secret"), stubServer.lastAuthorizationHeader.get());
        verify(algorithmExecutionService).markCancelled(execution.getId());
    }

    @Test
    void healthCheck_shouldUseTheAlgorithmsOwnCredentials() {
        final AlgorithmExecution execution = createExecutionWithCredentials(
                "algorithm-user", cipher.encrypt("algorithm-secret"), AlgorithmExecutionStatus.PENDING, null);

        adapter.healthCheck(execution);

        assertEquals(basic("algorithm-user", "algorithm-secret"), stubServer.lastAuthorizationHeader.get());
    }

    @Test
    void cancel_shouldCancelLocallyAndRestoreTheInterruptFlag_whenInterrupted() throws Exception {
        final AlgorithmExecution execution = createExecution("task-123", AlgorithmExecutionStatus.SUBMITTED);
        Thread.currentThread().interrupt();
        try {
            adapter.cancel(execution);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        verify(algorithmExecutionService).markCancelled(execution.getId());
    }

    private AlgorithmExecution createExecution(String externalRequestId, AlgorithmExecutionStatus status) {
        return AlgorithmExecution.builder()
                .id(UUID.randomUUID())
                .algorithm(Algorithm.builder()
                        .id(UUID.randomUUID())
                        .name("Test algorithm")
                        .serviceEndpoint(baseUrl())
                        .runEndpoint("/run")
                        .cancelEndpoint("/tasks/{taskId}")
                        .healthCheckEndpoint("/healthz")
                        .build())
                .triggerType(AlgorithmExecutionTriggerType.MANUAL)
                .executionStatus(status)
                .inputParameters("{\"value\":42}")
                .externalRequestId(externalRequestId)
                .build();
    }

    private AlgorithmExecution createAnalysisExecution(final UUID analysisId, final String inputParameters) {
        final Patient patient = mock(Patient.class);
        final Analysis analysis = mock(Analysis.class);

        when(analysis.getId()).thenReturn(analysisId);

        return AlgorithmExecution.builder()
                .id(UUID.randomUUID())
                .algorithm(Algorithm.builder()
                        .id(UUID.randomUUID())
                        .name("Test algorithm")
                        .serviceEndpoint(baseUrl())
                        .runEndpoint("/run")
                        .cancelEndpoint("/tasks/{taskId}")
                        .healthCheckEndpoint("/healthz")
                        .build())
                .patient(patient)
                .analysis(analysis)
                .triggerType(AlgorithmExecutionTriggerType.MANUAL)
                .executionStatus(AlgorithmExecutionStatus.PENDING)
                .inputParameters(inputParameters)
                .build();
    }

    private AlgorithmExecution createExecutionWithoutHealthEndpoint() {
        return AlgorithmExecution.builder()
                .id(UUID.randomUUID())
                .algorithm(Algorithm.builder()
                        .id(UUID.randomUUID())
                        .name("Test algorithm")
                        .serviceEndpoint(baseUrl())
                        .runEndpoint("/run")
                        .cancelEndpoint("/tasks/{taskId}")
                        .build())
                .triggerType(AlgorithmExecutionTriggerType.MANUAL)
                .executionStatus(AlgorithmExecutionStatus.PENDING)
                .inputParameters("{\"value\":42}")
                .build();
    }

    private static final class StubRemoteServer {
        private final AtomicInteger healthzCalls = new AtomicInteger();
        private final AtomicInteger runCalls = new AtomicInteger();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicInteger resultsCalls = new AtomicInteger();
        private final AtomicReference<String> lastRunRequestBody = new AtomicReference<>();
        private final AtomicReference<String> lastCancelPath = new AtomicReference<>();
        private final AtomicReference<String> lastResultsPath = new AtomicReference<>();
        private final AtomicReference<String> lastAuthorizationHeader = new AtomicReference<>();

        private int runStatusCode = 200;
        private long runDelayMs = 0;
        private int healthzStatusCode = 200;
        private String healthzResponseBody = "{\"status\":\"ok\"}";
        private String runResponseBody = "{\"prediction\":\"ok\"}";
        private String resultsResponseBody = "{\"status\":\"COMPLETED\",\"result\":{\"prediction\":\"ok\"}}";

        private void handleHealthz(HttpExchange exchange) throws IOException {
            healthzCalls.incrementAndGet();
            lastAuthorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, healthzStatusCode, healthzResponseBody);
        }

        private void handleRun(HttpExchange exchange) throws IOException {
            runCalls.incrementAndGet();
            lastAuthorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastRunRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (runDelayMs > 0) {
                try {
                    Thread.sleep(runDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            respond(exchange, runStatusCode, runResponseBody);
        }

        private void handleTasks(HttpExchange exchange) throws IOException {
            cancelCalls.incrementAndGet();
            lastAuthorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastCancelPath.set(exchange.getRequestURI().getPath());
            String cancelResponseBody = "{\"cancelled\":true}";
            int cancelStatusCode = 202;
            respond(exchange, cancelStatusCode, cancelResponseBody);
        }

        private void handleResults(HttpExchange exchange) throws IOException {
            resultsCalls.incrementAndGet();
            lastAuthorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastResultsPath.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, resultsResponseBody);
        }

        private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
