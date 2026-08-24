package si.result.project.eearly.facade.algorithmexecution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import si.result.project.eearly.config.algorithm.AlgorithmApiProperties;
import si.result.project.eearly.config.algorithm.AlgorithmHttpClient;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.port.algorithmexecution.AlgorithmDataPreparationService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.project.eearly.port.container.AlgorithmContainerService;
import si.result.project.eearly.util.JsonUtils;
import si.result.spring.boot.bricks.exception.DomainException;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpAlgorithmExecutionAdapter implements AlgorithmContainerService {

    private final AlgorithmExecutionService algorithmExecutionService;
    private final AlgorithmDataPreparationService algorithmDataPreparationService;
    private final AnalysisService analysisService;
    private final AnalysisServiceFacade analysisServiceFacade;
    private final AlgorithmApiProperties algorithmApiProperties;
    private final AlgorithmHttpClient algorithmHttpClient;

    @Override
    @Async
    public void run(AlgorithmExecution algorithmExecution) {
        try {
            final AlgorithmExecution executionContext = algorithmExecutionService.findInitializedById(
                    algorithmExecution.getId()
            );

            algorithmExecutionService.updateStatus(algorithmExecution.getId(),
                    AlgorithmExecutionStatus.SUBMITTED);
            updateAnalysisState(executionContext, AnalysisState.IN_PROGRESS);

            healthCheck(executionContext);

            algorithmExecutionService.updateStatus(algorithmExecution.getId(),
                    AlgorithmExecutionStatus.RUNNING);

            final java.net.http.HttpResponse<String> response = sendJsonRequest(
                    "POST",
                    executionContext.getAlgorithm().getExecutionUrl(),
                    buildRequestBody(executionContext)
            );

            if (!isSuccessful(response.statusCode())) {
                updateAnalysisState(executionContext, AnalysisState.ERROR);
                markFailed(algorithmExecution.getId(), buildHttpError(response));
                return;
            }

            final String responseBody = response.body();
            if (containsTaskId(responseBody)) {
                final String taskId = extractTaskId(responseBody);
                algorithmExecutionService.markRemoteTaskStarted(algorithmExecution.getId(), taskId);
                pollAsyncResult(executionContext, taskId);
                return;
            }

            handleSuccessfulResult(executionContext, responseBody);
        } catch (Exception e) {
            log.error("Remote execution failed for AlgorithmExecution {}",
                    algorithmExecution.getId(), e);
            if (algorithmExecution.getAnalysis() != null && algorithmExecution.getAnalysis().getId() != null) {
                try {
                    analysisService.updateState(algorithmExecution.getAnalysis().getId(), AnalysisState.ERROR);
                } catch (Exception analysisFailure) {
                    log.error("Failed to persist ERROR state for Analysis {}",
                            algorithmExecution.getAnalysis().getId(), analysisFailure);
                }
            }
            markFailed(algorithmExecution.getId(), e.getMessage());
        }
    }

    @Override
    public void cancel(AlgorithmExecution algorithmExecution) {
        final String externalRequestId = algorithmExecution.getExternalRequestId();

        if (externalRequestId != null && !externalRequestId.isBlank()) {
            try {
                sendJsonRequest("DELETE",
                        algorithmExecution.getAlgorithm().getCancellationUrl(externalRequestId),
                        null);
            } catch (Exception e) {
                log.warn(
                        "Remote cancellation failed for AlgorithmExecution {}. Cancelling locally.",
                        algorithmExecution.getId(), e);
            }
        }

        algorithmExecutionService.markCancelled(algorithmExecution.getId());
    }

    @Override
    public void healthCheck(AlgorithmExecution algorithmExecution) {
        if (algorithmExecution.getAlgorithm().getHealthCheckEndpoint() == null
                || algorithmExecution.getAlgorithm().getHealthCheckEndpoint().isBlank()) {
            return;
        }

        try {
            final java.net.http.HttpResponse<String> response = sendJsonRequest(
                    "GET",
                    algorithmExecution.getAlgorithm().getHealthUrl(),
                    null
            );

            if (!isSuccessful(response.statusCode())) {
                throw new IllegalStateException(
                        "Remote health check failed: " + buildHttpError(response));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Remote health check failed: " + e.getMessage(), e);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Remote health check failed: " + e.getMessage(), e);
        }
    }

    private java.net.http.HttpResponse<String> sendJsonRequest(
            String method, String url, String body) throws IOException, InterruptedException {
        return algorithmHttpClient.send(method, url, body, algorithmApiProperties.getDefaultTimeoutSeconds());
    }

    private String buildRequestBody(final AlgorithmExecution algorithmExecution) {
        if (algorithmExecution.getAnalysis() == null) {
            return defaultBody(algorithmExecution.getInputParameters());
        }

        final List<AnalysisSelection> selections = algorithmExecution.getInputSelections();
        if (selections.isEmpty()) {
            return defaultBody(algorithmExecution.getInputParameters());
        }

        return algorithmDataPreparationService.prepareInputData(
                algorithmExecution.getPatient(),
                algorithmExecution.getAlgorithm(),
                selections,
                algorithmExecution.getAnalysis().getId(),
                algorithmExecution.getAlgorithm().getRunnerConfig(),
                null
        );
    }

    private String defaultBody(final String inputParameters) {
        return inputParameters != null ? inputParameters : "{}";
    }

    private void pollAsyncResult(final AlgorithmExecution executionContext, final String taskId)
            throws IOException, InterruptedException {
        final Instant deadline = Instant.now().plusSeconds(algorithmApiProperties.getDefaultTimeoutSeconds());

        while (Instant.now().isBefore(deadline)) {
            final java.net.http.HttpResponse<String> response = sendJsonRequest(
                    "GET",
                    executionContext.getAlgorithm().getResultUrl(taskId),
                    null
            );

            if (!isSuccessful(response.statusCode())) {
                updateAnalysisState(executionContext, AnalysisState.ERROR);
                markFailed(executionContext.getId(), buildHttpError(response));
                return;
            }

            final AsyncResultState asyncResultState = parseAsyncResult(response.body());
            if (asyncResultState.isPending()) {
                Thread.sleep(algorithmApiProperties.getAsyncPollIntervalMs());
                continue;
            }

            if (asyncResultState.isFailure()) {
                updateAnalysisState(executionContext, AnalysisState.ERROR);
                markFailed(executionContext.getId(), asyncResultState.errorMessage());
                return;
            }

            handleSuccessfulResult(executionContext, asyncResultState.resultBody());
            return;
        }

        updateAnalysisState(executionContext, AnalysisState.ERROR);
        markFailed(executionContext.getId(), "Remote async execution timed out");
    }

    private void updateAnalysisState(final AlgorithmExecution executionContext, final AnalysisState state) {
        if (executionContext.getAnalysis() == null || executionContext.getAnalysis().getId() == null) {
            return;
        }
        analysisService.updateState(executionContext.getAnalysis().getId(), state);
    }

    private void handleSuccessfulResult(final AlgorithmExecution executionContext, final String responseBody) {
        updateAnalysisResultData(executionContext, responseBody);
        algorithmExecutionService.markCompleted(executionContext.getId(), responseBody);
    }

    private void updateAnalysisResultData(final AlgorithmExecution executionContext, final String responseBody) {
        if (executionContext.getAnalysis() == null || executionContext.getAnalysis().getId() == null) {
            return;
        }

        final UUID analysisId = executionContext.getAnalysis().getId();
        final ParsedAlgorithmAnalysisResult parsed = parseAlgorithmAnalysisResult(responseBody, analysisId);

        if (parsed == null) {
            analysisService.updateState(analysisId, AnalysisState.COMPLETED);
            return;
        }

        try {
            analysisServiceFacade.persistAlgorithmPredictions(
                    analysisId,
                    parsed.detected(),
                    parsed.detections(),
                    parsed.modelThreshold()
            );
        } catch (Exception exception) {
            log.warn("Failed to persist algorithm result for analysis {}", analysisId, exception);
            analysisService.updateState(analysisId, AnalysisState.COMPLETED);
        }
    }

    private record ParsedAlgorithmAnalysisResult(
            boolean detected,
            List<AnalysisDetectionDTO> detections,
            Double modelThreshold
    ) {}

    private ParsedAlgorithmAnalysisResult parseAlgorithmAnalysisResult(
            final String responseBody,
            final UUID analysisId) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            final JsonNode root = JsonUtils.mapper().readTree(responseBody);
            final JsonNode resultNode = selectMatchingResultNode(root.path("results"), analysisId);
            if (resultNode == null || resultNode.isMissingNode()) {
                return null;
            }

            final List<AnalysisDetectionDTO> detections = mapDetections(
                    resultNode.path("probabilities"),
                    resultNode.path("modelName").asText(null)
            );
            final Double modelThreshold = parseModelThreshold(resultNode);

            return new ParsedAlgorithmAnalysisResult(!detections.isEmpty(), detections, modelThreshold);
        } catch (Exception exception) {
            log.debug("Algorithm response did not match expected shape", exception);
            return null;
        }
    }

    private void markFailed(java.util.UUID executionId, String errorMessage) {
        try {
            algorithmExecutionService.markFailed(executionId, errorMessage);
        } catch (Exception saveFailure) {
            log.error("Failed to persist FAILED status for AlgorithmExecution {}",
                    executionId,
                    saveFailure);
        }
    }

    private String buildHttpError(java.net.http.HttpResponse<String> response) {
        return "HTTP " + response.statusCode() + ": " + response.body();
    }

    private boolean isSuccessful(int statusCode) {
        return HttpStatusCode.valueOf(statusCode).is2xxSuccessful();
    }

    private boolean containsTaskId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        try {
            final JsonNode jsonNode = JsonUtils.mapper().readTree(responseBody);
            return jsonNode.hasNonNull("task_id");
        } catch (Exception e) {
            return false;
        }
    }

    private String extractTaskId(String responseBody) {
        try {
            return JsonUtils.mapper().readTree(responseBody).path("task_id").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private AsyncResultState parseAsyncResult(final String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return AsyncResultState.pendingState();
        }

        try {
            final JsonNode jsonNode = JsonUtils.mapper().readTree(responseBody);
            final String status = extractAsyncStatus(jsonNode);

            if (status == null || status.isBlank()) {
                return AsyncResultState.completedState(responseBody);
            }

            final String normalizedStatus = status.trim().toUpperCase();
            if (normalizedStatus.equals("PENDING")
                    || normalizedStatus.equals("RUNNING")
                    || normalizedStatus.equals("IN_PROGRESS")
                    || normalizedStatus.equals("PROCESSING")
                    || normalizedStatus.equals("STARTED")) {
                return AsyncResultState.pendingState();
            }

            if (normalizedStatus.equals("FAILED")
                    || normalizedStatus.equals("ERROR")
                    || normalizedStatus.equals("CANCELLED")) {
                return AsyncResultState.failedState(extractAsyncErrorMessage(jsonNode));
            }

            if (normalizedStatus.equals("COMPLETED")
                    || normalizedStatus.equals("SUCCEEDED")
                    || normalizedStatus.equals("SUCCESS")
                    || normalizedStatus.equals("DONE")) {
                return AsyncResultState.completedState(extractAsyncResultBody(jsonNode, responseBody));
            }

            return AsyncResultState.completedState(responseBody);
        } catch (Exception exception) {
            return AsyncResultState.completedState(responseBody);
        }
    }

    private String extractAsyncStatus(final JsonNode jsonNode) {
        if (jsonNode.hasNonNull("status")) {
            return jsonNode.path("status").asText(null);
        }
        if (jsonNode.hasNonNull("state")) {
            return jsonNode.path("state").asText(null);
        }
        if (jsonNode.hasNonNull("task_status")) {
            return jsonNode.path("task_status").asText(null);
        }
        return null;
    }

    private String extractAsyncErrorMessage(final JsonNode jsonNode) {
        if (jsonNode.hasNonNull("error")) {
            return jsonNode.path("error").asText("Remote async execution failed");
        }
        if (jsonNode.hasNonNull("message")) {
            return jsonNode.path("message").asText("Remote async execution failed");
        }
        return "Remote async execution failed";
    }

    private String extractAsyncResultBody(final JsonNode jsonNode, final String fallbackBody) {
        try {
            if (jsonNode.has("result")) {
                return JsonUtils.mapper().writeValueAsString(jsonNode.path("result"));
            }
            if (jsonNode.has("response")) {
                return JsonUtils.mapper().writeValueAsString(jsonNode.path("response"));
            }
            if (jsonNode.has("output")) {
                return JsonUtils.mapper().writeValueAsString(jsonNode.path("output"));
            }
        } catch (Exception exception) {
            return fallbackBody;
        }
        return fallbackBody;
    }

    private JsonNode selectMatchingResultNode(final JsonNode resultsNode, final UUID analysisId) {
        if (!resultsNode.isArray() || resultsNode.isEmpty()) {
            return null;
        }

        for (JsonNode candidate : resultsNode) {
            if (analysisId.toString().equals(candidate.path("analysisId").asText(null))) {
                return candidate;
            }
        }

        return resultsNode.size() == 1 ? resultsNode.get(0) : null;
    }

    private static Double parseModelThreshold(final JsonNode resultNode) {
        if (resultNode == null) {
            return null;
        }
        final Double snakeCase = readNonNullDouble(resultNode, "model_threshold");
        if (snakeCase != null) {
            return snakeCase;
        }
        return readNonNullDouble(resultNode, "modelThreshold");
    }

    private static Double readNonNullDouble(final JsonNode node, final String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asDouble();
    }

    private List<AnalysisDetectionDTO> mapDetections(
            final JsonNode probabilitiesNode,
            final String modelName) {
        if (!probabilitiesNode.isArray() || probabilitiesNode.isEmpty()) {
            return List.of();
        }

        final java.util.ArrayList<AnalysisDetectionDTO> detections = new java.util.ArrayList<>();
        for (JsonNode probabilityNode : probabilitiesNode) {
            final OffsetDateTime startTimestamp = parseOffsetDateTime(
                    probabilityNode.path("probStartTime").asText(null)
            );
            final OffsetDateTime endTimestamp = parseOffsetDateTime(
                    probabilityNode.path("probEndTime").asText(null)
            );
            if (startTimestamp == null || endTimestamp == null) {
                continue;
            }

            final JsonNode probabilityValue = probabilityNode.path("probability");
            detections.add(new AnalysisDetectionDTO(
                    UUID.randomUUID(),
                    startTimestamp,
                    endTimestamp,
                    probabilityValue.isNumber() ? probabilityValue.doubleValue() : null,
                    null,
                    DetectionBoxSources.MODEL_PREDICTION
            ));
        }
        return List.copyOf(detections);
    }

    private OffsetDateTime parseOffsetDateTime(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private record AsyncResultState(String resultBody, String errorMessage, boolean pending, boolean failure) {

        private static AsyncResultState pendingState() {
            return new AsyncResultState(null, null, true, false);
        }

        private static AsyncResultState completedState(final String resultBody) {
            return new AsyncResultState(resultBody, null, false, false);
        }

        private static AsyncResultState failedState(final String errorMessage) {
            return new AsyncResultState(null, errorMessage, false, true);
        }

        private boolean isPending() {
            return pending;
        }

        private boolean isFailure() {
            return failure;
        }
    }
}
