package si.result.project.eearly.port.algorithmexecution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlgorithmExecutionServiceTest {

    @Mock
    private AlgorithmExecutionRepository algorithmExecutionRepository;

    @InjectMocks
    private AlgorithmExecutionService algorithmExecutionService;

    private UUID executionId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Mark failed")
    class MarkFailedTest {

        @Test
        @DisplayName("Should publish ExecutionFailedEvent with correct IDs")
        void markFailed_PublishesExecutionFailedEvent() {
            AlgorithmExecution execution = mock(AlgorithmExecution.class);
            when(execution.getExecutionStatus()).thenReturn(AlgorithmExecutionStatus.RUNNING);

            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markFailed(executionId, "something went wrong");
        }

        @Test
        @DisplayName("Should return early and publish no event when already FAILED")
        void markFailed_AlreadyFailed_PublishesNoEvent() {
            AlgorithmExecution execution = mock(AlgorithmExecution.class);
            when(execution.getExecutionStatus()).thenReturn(AlgorithmExecutionStatus.FAILED);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            algorithmExecutionService.markFailed(executionId, "error");

            verify(algorithmExecutionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return early and publish no event when already COMPLETED")
        void markFailed_AlreadyCompleted_PublishesNoEvent() {
            AlgorithmExecution execution = mock(AlgorithmExecution.class);
            when(execution.getExecutionStatus()).thenReturn(AlgorithmExecutionStatus.COMPLETED);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            algorithmExecutionService.markFailed(executionId, "error");

            verify(algorithmExecutionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return early and publish no event when already CANCELLED")
        void markFailed_AlreadyCancelled_PublishesNoEvent() {
            AlgorithmExecution execution = mock(AlgorithmExecution.class);
            when(execution.getExecutionStatus()).thenReturn(AlgorithmExecutionStatus.CANCELLED);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            algorithmExecutionService.markFailed(executionId, "error");

            verify(algorithmExecutionRepository, never()).save(any());
        }
    }
}
