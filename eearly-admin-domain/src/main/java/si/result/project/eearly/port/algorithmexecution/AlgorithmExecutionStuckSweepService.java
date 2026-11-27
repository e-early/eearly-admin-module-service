package si.result.project.eearly.port.algorithmexecution;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import si.result.project.eearly.config.AlgorithmExecutionStuckSweepProperties;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.port.analysis.AnalysisService;

/**
 * Fails an algorithm execution that has sat in SUBMITTED or RUNNING past a timeout with no result, and puts its
 * analysis into ERROR (which publishes the state event as every other change of state does).
 *
 * <p>The HTTP adapter has its own deadline inside its polling loop, and the simulation always completes within
 * seconds, so in practice this matters for a webhook-style provider whose remote pipeline failed before it ever
 * called back: there is no error callback to report that, so nothing else would ever unstick the execution. The
 * timeout must therefore exceed the adapter timeout (checked at startup by
 * {@link AlgorithmExecutionStuckSweepSettingsValidator}), or an execution the adapter is still waiting for would be
 * failed under its feet.
 *
 * <p>An analysis that has already moved on (a result arrived, or it was confirmed) is left alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "algorithm.stuck-execution-sweep", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class AlgorithmExecutionStuckSweepService {

    private static final List<AlgorithmExecutionStatus> STUCK_STATUSES =
            List.of(AlgorithmExecutionStatus.SUBMITTED, AlgorithmExecutionStatus.RUNNING);

    private static final List<AnalysisState> OPEN_ANALYSIS_STATES =
            List.of(AnalysisState.ORDERED, AnalysisState.IN_PROGRESS);

    private final AlgorithmExecutionService algorithmExecutionService;
    private final AlgorithmExecutionStuckSweepProperties properties;
    private final AnalysisService analysisService;

    @Scheduled(fixedDelayString = "${algorithm.stuck-execution-sweep.poll-interval-ms:300000}")
    public void sweepStuckExecutions() {
        final ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(properties.getTimeoutMinutes());
        final List<AlgorithmExecution> stuck = algorithmExecutionService.findStuckExecutions(STUCK_STATUSES, cutoff);
        for (final AlgorithmExecution execution : stuck) {
            try {
                failStuckExecution(execution);
            } catch (Exception exception) {
                // One execution that cannot be failed must not keep the others stuck.
                log.error("Failed to fail stuck AlgorithmExecution {}", execution.getId(), exception);
            }
        }
    }

    private void failStuckExecution(final AlgorithmExecution execution) {
        log.warn("Failing stuck AlgorithmExecution {} (status={}, lastUpdated={})",
                execution.getId(), execution.getExecutionStatus(), execution.getLastModifiedAt());

        algorithmExecutionService.markFailed(execution.getId(),
                "Execution timed out: no result received within " + properties.getTimeoutMinutes() + " minutes");

        final Analysis analysis = execution.getAnalysis();
        if (analysis == null || analysis.getId() == null) {
            return;
        }
        if (!OPEN_ANALYSIS_STATES.contains(analysis.getState())) {
            log.info("Analysis {} is already {}; not marking it ERROR for stuck AlgorithmExecution {}",
                    analysis.getId(), analysis.getState(), execution.getId());
            return;
        }
        try {
            analysisService.updateState(analysis.getId(), AnalysisState.ERROR);
        } catch (Exception exception) {
            log.error("Failed to mark Analysis ERROR for stuck AlgorithmExecution {}", execution.getId(), exception);
        }
    }
}
