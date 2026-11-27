package si.result.project.eearly.facade.algorithmexecution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.dto.analysis.AnalysisResultCallbackDTO;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;

@ExtendWith(MockitoExtension.class)
class AlgorithmSimulationServiceTest {

    @Mock
    private AlgorithmExecutionService algorithmExecutionService;

    @Mock
    private AnalysisServiceFacade analysisServiceFacade;

    private AlgorithmSimulationService service;

    @BeforeEach
    void setUp() {
        service = new AlgorithmSimulationService(algorithmExecutionService, analysisServiceFacade, 0L);
    }

    @Test
    void run_shouldStoreSimulatedDetectionsAndCompleteExecutionMarkedAsSimulation() {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = executionWithAnalysis(analysisId);

        service.run(execution);

        final InOrder order = inOrder(algorithmExecutionService, analysisServiceFacade);
        order.verify(algorithmExecutionService).updateStatus(execution.getId(), AlgorithmExecutionStatus.SUBMITTED);
        order.verify(algorithmExecutionService).updateStatus(execution.getId(), AlgorithmExecutionStatus.RUNNING);
        final ArgumentCaptor<AnalysisResultCallbackDTO> callback =
                ArgumentCaptor.forClass(AnalysisResultCallbackDTO.class);
        order.verify(analysisServiceFacade).updateResult(eq(analysisId), callback.capture());
        order.verify(algorithmExecutionService).markCompleted(
                execution.getId(), AlgorithmSimulationService.SIMULATION_RESULT_DATA);

        assertEquals("{\"source\":\"simulation\"}", AlgorithmSimulationService.SIMULATION_RESULT_DATA);
        assertEquals(analysisId, callback.getValue().analysisId());
        assertEquals(AnalysisState.WAITING_FOR_CONFIRMATION, callback.getValue().state());
        assertTrue(callback.getValue().detected());
        assertEquals(2, callback.getValue().detections().size());
        for (final AnalysisDetectionDTO detection : callback.getValue().detections()) {
            assertEquals(DetectionBoxSources.MODEL_PREDICTION, detection.source());
        }
        verify(algorithmExecutionService, never()).markFailed(any(), any());
    }

    @Test
    void run_shouldFailExecution_whenThereIsNoLinkedAnalysis() {
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(UUID.randomUUID()).build();

        service.run(execution);

        verify(algorithmExecutionService).markFailed(execution.getId(), "Simulation requires analysisId");
        verifyNoInteractions(analysisServiceFacade);
        verify(algorithmExecutionService, never()).markCompleted(any(), any());
    }

    @Test
    void run_shouldFailExecution_whenStoringTheResultFails() {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = executionWithAnalysis(analysisId);
        when(analysisServiceFacade.updateResult(eq(analysisId), any()))
                .thenThrow(new IllegalStateException("storage down"));

        service.run(execution);

        verify(algorithmExecutionService).markFailed(execution.getId(), "storage down");
        verify(algorithmExecutionService, never()).markCompleted(any(), any());
    }

    @Test
    void run_shouldFailExecutionInsteadOfCompleting_whenInterruptedWhileWaiting() {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = executionWithAnalysis(analysisId);
        final AlgorithmSimulationService slow =
                new AlgorithmSimulationService(algorithmExecutionService, analysisServiceFacade, 60_000L);

        Thread.currentThread().interrupt();
        try {
            slow.run(execution);
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be preserved");
        } finally {
            Thread.interrupted();
        }

        verify(algorithmExecutionService).markFailed(execution.getId(), "Simulation interrupted");
        verify(algorithmExecutionService, never()).markCompleted(any(), any());
        verifyNoInteractions(analysisServiceFacade);
    }

    @Test
    void cancel_shouldMarkExecutionCancelled() {
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(UUID.randomUUID()).build();

        service.cancel(execution);

        verify(algorithmExecutionService).markCancelled(execution.getId());
    }

    @Test
    void healthCheck_shouldDoNothing() {
        service.healthCheck(AlgorithmExecution.builder().id(UUID.randomUUID()).build());

        verifyNoInteractions(algorithmExecutionService, analysisServiceFacade);
    }

    private static AlgorithmExecution executionWithAnalysis(final UUID analysisId) {
        final Analysis analysis = mock(Analysis.class);
        when(analysis.getId()).thenReturn(analysisId);
        return AlgorithmExecution.builder().id(UUID.randomUUID()).analysis(analysis).build();
    }
}
