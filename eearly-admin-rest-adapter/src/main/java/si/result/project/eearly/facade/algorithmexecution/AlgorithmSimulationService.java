package si.result.project.eearly.facade.algorithmexecution;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.dto.analysis.AnalysisResultCallbackDTO;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.container.AlgorithmContainerService;

/**
 * Stand-in for the algorithm containers, active only with the Spring profile {@code simulation}
 * (for example {@code SPRING_PROFILES_ACTIVE=simulation}). It replaces
 * {@link HttpAlgorithmExecutionAdapter}, so analyses can be run end to end without any algorithm container.
 *
 * <p>The result is <strong>fabricated demo data</strong>, not the output of a model. It must never be used
 * clinically. Every execution completed here is marked by the result data {@value #SIMULATION_RESULT_DATA}.
 */
@Slf4j
@Service
@Profile(AlgorithmSimulationService.PROFILE)
public class AlgorithmSimulationService implements AlgorithmContainerService {

    public static final String PROFILE = "simulation";

    /** Stored as the result data of every execution completed by the simulation. */
    public static final String SIMULATION_RESULT_DATA = "{\"source\":\"simulation\"}";

    private final AlgorithmExecutionService algorithmExecutionService;
    private final AnalysisServiceFacade analysisServiceFacade;
    private final long delayMs;

    public AlgorithmSimulationService(
            final AlgorithmExecutionService algorithmExecutionService,
            final AnalysisServiceFacade analysisServiceFacade,
            @Value("${simulation.delay-ms:3000}") final long delayMs) {
        this.algorithmExecutionService = algorithmExecutionService;
        this.analysisServiceFacade = analysisServiceFacade;
        this.delayMs = delayMs;
    }

    @PostConstruct
    void warnAboutSimulation() {
        log.warn("Profile '{}' is active: algorithm executions are simulated and produce fabricated results. "
                + "Do not use this profile with real patients.", PROFILE);
    }

    @Override
    @Async
    public void run(final AlgorithmExecution algorithmExecution) {
        try {
            algorithmExecutionService.updateStatus(
                    algorithmExecution.getId(),
                    AlgorithmExecutionStatus.SUBMITTED
            );
            algorithmExecutionService.updateStatus(
                    algorithmExecution.getId(),
                    AlgorithmExecutionStatus.RUNNING
            );

            final Analysis analysis = algorithmExecution.getAnalysis();
            if (analysis == null || analysis.getId() == null) {
                algorithmExecutionService.markFailed(
                        algorithmExecution.getId(),
                        "Simulation requires analysisId"
                );
                return;
            }

            if (!pause()) {
                algorithmExecutionService.markFailed(algorithmExecution.getId(), "Simulation interrupted");
                return;
            }

            log.info("Simulating algorithm result for analysisId={}", analysis.getId());
            analysisServiceFacade.updateResult(analysis.getId(), simulatedResult(analysis.getId()));
            algorithmExecutionService.markCompleted(algorithmExecution.getId(), SIMULATION_RESULT_DATA);
        } catch (Exception e) {
            log.error("Simulation execution failed for AlgorithmExecution {}", algorithmExecution.getId(), e);
            algorithmExecutionService.markFailed(algorithmExecution.getId(), e.getMessage());
        }
    }

    @Override
    public void cancel(final AlgorithmExecution algorithmExecution) {
        algorithmExecutionService.markCancelled(algorithmExecution.getId());
    }

    @Override
    public void healthCheck(final AlgorithmExecution algorithmExecution) {
        // nothing to check: there is no algorithm container in simulation mode
    }

    private boolean pause() {
        if (delayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static AnalysisResultCallbackDTO simulatedResult(final UUID analysisId) {
        final List<AnalysisDetectionDTO> detections = List.of(
                new AnalysisDetectionDTO(
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-08-18T10:28:37+02:00"),
                        OffsetDateTime.parse("2026-08-18T10:29:00+02:00"),
                        0.58,
                        null,
                        DetectionBoxSources.MODEL_PREDICTION
                ),
                new AnalysisDetectionDTO(
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-08-18T10:29:15+02:00"),
                        OffsetDateTime.parse("2026-08-18T10:30:30+02:00"),
                        0.84,
                        null,
                        DetectionBoxSources.MODEL_PREDICTION
                )
        );

        return new AnalysisResultCallbackDTO(
                UUID.randomUUID(),
                analysisId,
                AnalysisState.WAITING_FOR_CONFIRMATION,
                true,
                "Apnea detection",
                detections
        );
    }
}
