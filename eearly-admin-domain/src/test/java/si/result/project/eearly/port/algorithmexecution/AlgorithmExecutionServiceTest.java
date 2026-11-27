package si.result.project.eearly.port.algorithmexecution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import si.result.project.eearly.event.notification.event.ExecutionCompletedEvent;
import si.result.project.eearly.event.notification.event.ExecutionFailedEvent;
import si.result.project.eearly.event.notification.event.ExecutionStartedEvent;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.patient.Patient;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmExecutionServiceTest {

    @Mock
    private AlgorithmExecutionRepository algorithmExecutionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlgorithmExecutionService algorithmExecutionService;

    private UUID executionId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        patientId = UUID.randomUUID();
    }

    private AlgorithmExecution execution(final AlgorithmExecutionStatus status,
            final AlgorithmExecutionTriggerType triggerType, final String algorithmName, final boolean withPatient,
            final boolean withAnalysis) {
        final AlgorithmExecution execution = mock(AlgorithmExecution.class);
        lenient().when(execution.getId()).thenReturn(executionId);
        lenient().when(execution.getExecutionStatus()).thenReturn(status);
        lenient().when(execution.getTriggerType()).thenReturn(triggerType);
        if (algorithmName != null) {
            final Algorithm algorithm = mock(Algorithm.class);
            lenient().when(algorithm.getName()).thenReturn(algorithmName);
            lenient().when(execution.getAlgorithm()).thenReturn(algorithm);
        }
        if (withPatient) {
            final Patient patient = mock(Patient.class);
            lenient().when(patient.getId()).thenReturn(patientId);
            lenient().when(execution.getPatient()).thenReturn(patient);
        }
        if (withAnalysis) {
            lenient().when(execution.getAnalysis()).thenReturn(mock(Analysis.class));
        }
        return execution;
    }

    @Nested
    @DisplayName("Create")
    class CreateTest {

        private CreateAlgorithmExecutionCommand command(final AlgorithmExecutionTriggerType triggerType) {
            final Patient patient = mock(Patient.class);
            lenient().when(patient.getId()).thenReturn(patientId);
            return new CreateAlgorithmExecutionCommand(mock(Algorithm.class), patient, null, triggerType, "{}");
        }

        @Test
        @DisplayName("Should publish ExecutionStartedEvent for a scheduled execution of a patient")
        void create_Scheduled_PublishesExecutionStartedEvent() {
            final AlgorithmExecution saved = execution(AlgorithmExecutionStatus.PENDING,
                    AlgorithmExecutionTriggerType.SCHEDULED, "Apnea detection", true, false);
            when(algorithmExecutionRepository.save(any())).thenReturn(saved);

            algorithmExecutionService.create(command(AlgorithmExecutionTriggerType.SCHEDULED));

            verify(eventPublisher).publishEvent(new ExecutionStartedEvent(executionId, patientId, "Apnea detection"));
            verifyNoMoreInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should publish no event for a manual execution")
        void create_Manual_PublishesNoEvent() {
            final AlgorithmExecution saved = execution(AlgorithmExecutionStatus.PENDING,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", true, false);
            when(algorithmExecutionRepository.save(any())).thenReturn(saved);

            algorithmExecutionService.create(command(AlgorithmExecutionTriggerType.MANUAL));

            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("Mark completed")
    class MarkCompletedTest {

        @Test
        @DisplayName("Should publish ExecutionCompletedEvent for a scheduled execution without analysis")
        void markCompleted_ScheduledWithoutAnalysis_PublishesEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING,
                    AlgorithmExecutionTriggerType.SCHEDULED, "Apnea detection", true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markCompleted(executionId, "{}");

            verify(eventPublisher).publishEvent(
                    new ExecutionCompletedEvent(executionId, patientId, "Apnea detection"));
            verifyNoMoreInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should publish no event when the execution produced an analysis")
        void markCompleted_WithAnalysis_PublishesNoEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING,
                    AlgorithmExecutionTriggerType.SCHEDULED, "Apnea detection", true, true);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markCompleted(executionId, "{}");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should publish no event for a manual execution")
        void markCompleted_Manual_PublishesNoEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markCompleted(executionId, "{}");

            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("Mark failed")
    class MarkFailedTest {

        @Test
        @DisplayName("Should publish ExecutionFailedEvent with correct IDs")
        void markFailed_PublishesExecutionFailedEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markFailed(executionId, "something went wrong");

            verify(eventPublisher).publishEvent(new ExecutionFailedEvent(executionId, patientId, "Apnea detection"));
            verifyNoMoreInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should fall back to a generic algorithm name when the algorithm is unknown")
        void markFailed_WithoutAlgorithm_UsesGenericName() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING,
                    AlgorithmExecutionTriggerType.MANUAL, null, true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markFailed(executionId, "error");

            verify(eventPublisher).publishEvent(new ExecutionFailedEvent(executionId, patientId, "Algorithm"));
        }

        @Test
        @DisplayName("Should publish no event when the execution has no patient")
        void markFailed_WithoutPatient_PublishesNoEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", false, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(algorithmExecutionRepository.save(any())).thenReturn(execution);

            algorithmExecutionService.markFailed(executionId, "error");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should return early and publish no event when already FAILED")
        void markFailed_AlreadyFailed_PublishesNoEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.FAILED,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            algorithmExecutionService.markFailed(executionId, "error");

            verify(algorithmExecutionRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should return early and publish no event when already COMPLETED")
        void markFailed_AlreadyCompleted_PublishesNoEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.COMPLETED,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            algorithmExecutionService.markFailed(executionId, "error");

            verify(algorithmExecutionRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should return early and publish no event when already CANCELLED")
        void markFailed_AlreadyCancelled_PublishesNoEvent() {
            final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.CANCELLED,
                    AlgorithmExecutionTriggerType.MANUAL, "Apnea detection", true, false);
            when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            algorithmExecutionService.markFailed(executionId, "error");

            verify(algorithmExecutionRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }
}
