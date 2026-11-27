package si.result.project.eearly.port.algorithmexecution;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithmexecution.ScheduledMeasurementWindowStrategy.MeasurementWindow;

/**
 * Builds the request body sent to an algorithm for an execution: from the selections of its analysis, else from the
 * input schema of the algorithm, else the stored input parameters.
 *
 * <p>When callback tokens are enabled (see {@link CallbackTokenService}) and the execution has an analysis, a callback
 * URL {@code {service.domain}/api/v1/analyses/{id}/result?token=...} with a freshly issued single-use token is passed
 * to the data preparation, which puts it into the payload only if the algorithm's input schema declares a
 * {@code callbackUrl} property. Without callback tokens there is no callback URL. The URL is a credential: it is
 * never logged.
 */
@Component
public class AlgorithmExecutionPayloadBuilder {

    private final AlgorithmDataPreparationService algorithmDataPreparationService;
    private final CallbackTokenService callbackTokenService;
    private final ScheduledMeasurementWindowStrategy scheduledMeasurementWindowStrategy;
    private final String serviceDomain;

    @Autowired
    public AlgorithmExecutionPayloadBuilder(
            final AlgorithmDataPreparationService algorithmDataPreparationService,
            final CallbackTokenService callbackTokenService,
            final ObjectProvider<ScheduledMeasurementWindowStrategy> scheduledMeasurementWindowStrategy,
            @Value("${service.domain}") final String serviceDomain) {
        this(algorithmDataPreparationService, callbackTokenService,
                scheduledMeasurementWindowStrategy.orderedStream().findFirst()
                        .orElse(ScheduledMeasurementWindowStrategy.NONE),
                serviceDomain);
    }

    public AlgorithmExecutionPayloadBuilder(
            final AlgorithmDataPreparationService algorithmDataPreparationService,
            final CallbackTokenService callbackTokenService,
            final ScheduledMeasurementWindowStrategy scheduledMeasurementWindowStrategy,
            final String serviceDomain) {
        this.algorithmDataPreparationService = algorithmDataPreparationService;
        this.callbackTokenService = callbackTokenService;
        this.scheduledMeasurementWindowStrategy = scheduledMeasurementWindowStrategy;
        this.serviceDomain = serviceDomain;
    }

    /** Whether the payloads carry a callback URL, that is whether callback tokens are enabled. */
    public boolean callbackUrlsEnabled() {
        return callbackTokenService.isEnabled();
    }

    public String buildPayload(final AlgorithmExecution algorithmExecution) {
        if (algorithmExecution.getAnalysis() != null) {
            final List<AnalysisSelection> selections = algorithmExecution.getInputSelections();
            if (!selections.isEmpty()) {
                return algorithmDataPreparationService.prepareInputData(
                        algorithmExecution.getPatient(),
                        algorithmExecution.getAlgorithm(),
                        selections,
                        algorithmExecution.getAnalysis().getId(),
                        algorithmExecution.getAlgorithm().getRunnerConfig(),
                        resolveCallbackUrl(algorithmExecution)
                );
            }
        }

        if (canPrepareFromAlgorithmSchema(algorithmExecution)) {
            return prepareFromAlgorithmSchema(algorithmExecution);
        }

        return defaultBody(algorithmExecution.getInputParameters());
    }

    /**
     * The URL the provider reports its result to, with a freshly issued single-use token, or {@code null} when callback
     * tokens are not enabled or the execution has no analysis to correlate the callback with. The provider never
     * inspects the token: it posts back to exactly the URL it was given, which is what authenticates the callback.
     */
    private String resolveCallbackUrl(final AlgorithmExecution algorithmExecution) {
        if (!callbackTokenService.isEnabled()) {
            return null;
        }
        if (algorithmExecution.getAnalysis() == null || algorithmExecution.getAnalysis().getId() == null) {
            return null;
        }
        final String baseUrl = trimTrailingSlashes(serviceDomain)
                + "/api/v1/analyses/" + algorithmExecution.getAnalysis().getId() + "/result";
        return baseUrl + "?token=" + callbackTokenService.issueToken(algorithmExecution.getId());
    }

    private boolean canPrepareFromAlgorithmSchema(final AlgorithmExecution algorithmExecution) {
        final Patient patient = algorithmExecution.getPatient();
        final Algorithm algorithm = algorithmExecution.getAlgorithm();
        if (patient == null || algorithm == null) {
            return false;
        }

        final String inputSchema = algorithm.getInputSchema();
        return inputSchema != null && !inputSchema.isBlank();
    }

    private String prepareFromAlgorithmSchema(final AlgorithmExecution algorithmExecution) {
        final Patient patient = algorithmExecution.getPatient();
        final Algorithm algorithm = algorithmExecution.getAlgorithm();
        final String runnerConfig = algorithm.getRunnerConfig();

        if (algorithmExecution.getTriggerType() == AlgorithmExecutionTriggerType.SCHEDULED) {
            final Optional<MeasurementWindow> window = scheduledMeasurementWindowStrategy.resolve(algorithmExecution);
            if (window.isPresent()) {
                return algorithmDataPreparationService.prepareInputData(
                        patient,
                        algorithm,
                        window.get().start(),
                        window.get().end(),
                        algorithmExecution.getId(),
                        null,
                        runnerConfig,
                        resolveCallbackUrl(algorithmExecution)
                );
            }
        }

        return algorithmDataPreparationService.prepareInputData(
                patient,
                algorithm,
                List.of(),
                null,
                runnerConfig,
                resolveCallbackUrl(algorithmExecution)
        );
    }

    private static String trimTrailingSlashes(final String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private String defaultBody(final String inputParameters) {
        return inputParameters != null ? inputParameters : "{}";
    }
}
