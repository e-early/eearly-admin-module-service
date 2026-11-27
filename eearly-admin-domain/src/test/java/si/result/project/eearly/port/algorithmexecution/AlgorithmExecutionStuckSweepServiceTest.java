package si.result.project.eearly.port.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import si.result.project.eearly.config.AlgorithmExecutionStuckSweepProperties;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.port.analysis.AnalysisService;

@ExtendWith(MockitoExtension.class)
class AlgorithmExecutionStuckSweepServiceTest {

    @Mock
    private AlgorithmExecutionService algorithmExecutionService;
    @Mock
    private AnalysisService analysisService;

    private AlgorithmExecutionStuckSweepProperties properties;
    private AlgorithmExecutionStuckSweepService sweep;

    @BeforeEach
    void setUp() {
        properties = new AlgorithmExecutionStuckSweepProperties();
        sweep = new AlgorithmExecutionStuckSweepService(algorithmExecutionService, properties, analysisService);
    }

    private AlgorithmExecution stuck(final UUID id, final UUID analysisId, final AnalysisState state) {
        final Analysis analysis = mock(Analysis.class);
        org.mockito.Mockito.lenient().when(analysis.getId()).thenReturn(analysisId);
        org.mockito.Mockito.lenient().when(analysis.getState()).thenReturn(state);
        return AlgorithmExecution.builder().id(id).analysis(analysis)
                .executionStatus(AlgorithmExecutionStatus.RUNNING).build();
    }

    @Test
    void defaults_areSafe() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPollIntervalMs()).isEqualTo(300_000);
        assertThat(properties.getTimeoutMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("The sweep can be switched off and is polled on the configured interval")
    void sweepIsConfigurable() throws NoSuchMethodException {
        final ConditionalOnProperty conditional =
                AlgorithmExecutionStuckSweepService.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(conditional.prefix()).isEqualTo("algorithm.stuck-execution-sweep");
        assertThat(conditional.name()).containsExactly("enabled");
        assertThat(conditional.matchIfMissing()).isTrue();
        assertThat(AlgorithmExecutionStuckSweepService.class.getMethod("sweepStuckExecutions")
                .getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${algorithm.stuck-execution-sweep.poll-interval-ms:300000}");
    }

    @Test
    @DisplayName("Looks only at SUBMITTED and RUNNING executions older than the timeout")
    void queriesStuckStatusesWithTheCutoff() {
        properties.setTimeoutMinutes(120);
        when(algorithmExecutionService.findStuckExecutions(anyList(), any())).thenReturn(List.of());
        final ZonedDateTime before = ZonedDateTime.now();

        sweep.sweepStuckExecutions();

        final ArgumentCaptor<ZonedDateTime> cutoff = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(algorithmExecutionService).findStuckExecutions(
                eq(List.of(AlgorithmExecutionStatus.SUBMITTED, AlgorithmExecutionStatus.RUNNING)), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusMinutes(120).minusSeconds(1),
                ZonedDateTime.now().minusMinutes(120).plusSeconds(1));
        verify(algorithmExecutionService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("A stuck execution is failed and its open analysis is set to ERROR")
    void failsStuckExecutionAndAnalysis() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = stuck(executionId, analysisId, AnalysisState.IN_PROGRESS);
        when(algorithmExecutionService.findStuckExecutions(anyList(), any())).thenReturn(List.of(execution));

        sweep.sweepStuckExecutions();

        verify(algorithmExecutionService).markFailed(eq(executionId), org.mockito.ArgumentMatchers.contains("90 minutes"));
        verify(analysisService).updateState(analysisId, AnalysisState.ERROR);
    }

    @Test
    @DisplayName("An analysis that is no longer open is left alone")
    void leavesAnalysisThatIsNotOpen() {
        for (final AnalysisState state : new AnalysisState[] {AnalysisState.COMPLETED, AnalysisState.ERROR}) {
            final UUID executionId = UUID.randomUUID();
            final AlgorithmExecution execution = stuck(executionId, UUID.randomUUID(), state);
            when(algorithmExecutionService.findStuckExecutions(anyList(), any())).thenReturn(List.of(execution));

            sweep.sweepStuckExecutions();

            verify(algorithmExecutionService).markFailed(eq(executionId), anyString());
        }
        verify(analysisService, never()).updateState(any(), any());
    }

    @Test
    void executionWithoutAnalysis_isOnlyFailed() {
        final UUID executionId = UUID.randomUUID();
        when(algorithmExecutionService.findStuckExecutions(anyList(), any())).thenReturn(List.of(
                AlgorithmExecution.builder().id(executionId).executionStatus(AlgorithmExecutionStatus.SUBMITTED).build()));

        sweep.sweepStuckExecutions();

        verify(algorithmExecutionService).markFailed(eq(executionId), anyString());
        verify(analysisService, never()).updateState(any(), any());
    }

    @Test
    @DisplayName("One execution that cannot be failed does not keep the others stuck")
    void failureOfOneExecutionIsIsolated() {
        final UUID broken = UUID.randomUUID();
        final UUID healthy = UUID.randomUUID();
        final UUID healthyAnalysis = UUID.randomUUID();
        final AlgorithmExecution brokenExecution = stuck(broken, UUID.randomUUID(), AnalysisState.IN_PROGRESS);
        final AlgorithmExecution healthyExecution = stuck(healthy, healthyAnalysis, AnalysisState.ORDERED);
        when(algorithmExecutionService.findStuckExecutions(anyList(), any()))
                .thenReturn(List.of(brokenExecution, healthyExecution));
        doThrow(new IllegalStateException("boom")).when(algorithmExecutionService).markFailed(eq(broken), anyString());

        sweep.sweepStuckExecutions();

        verify(algorithmExecutionService).markFailed(eq(healthy), anyString());
        verify(analysisService).updateState(healthyAnalysis, AnalysisState.ERROR);
        verify(analysisService, never()).updateState(any(), eq(AnalysisState.COMPLETED));
    }

    @Test
    @DisplayName("A failing analysis update does not stop the sweep")
    void analysisUpdateFailureIsIsolated() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final UUID firstAnalysis = UUID.randomUUID();
        final AlgorithmExecution firstExecution = stuck(first, firstAnalysis, AnalysisState.IN_PROGRESS);
        final AlgorithmExecution secondExecution = stuck(second, UUID.randomUUID(), AnalysisState.IN_PROGRESS);
        when(algorithmExecutionService.findStuckExecutions(anyList(), any()))
                .thenReturn(List.of(firstExecution, secondExecution));
        doThrow(new IllegalStateException("boom")).when(analysisService).updateState(firstAnalysis, AnalysisState.ERROR);

        sweep.sweepStuckExecutions();

        verify(algorithmExecutionService).markFailed(eq(second), anyString());
    }
}
