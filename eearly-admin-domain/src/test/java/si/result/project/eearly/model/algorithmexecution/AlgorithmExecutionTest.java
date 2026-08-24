package si.result.project.eearly.model.algorithmexecution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.patient.Patient;

import java.util.UUID;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlgorithmExecutionTest {

    private AlgorithmExecution execution;

    @BeforeEach
    void setUp() {
        execution = AlgorithmExecution.builder()
                .id(UUID.randomUUID())
                .executionStatus(AlgorithmExecutionStatus.PENDING)
                .triggerType(AlgorithmExecutionTriggerType.MANUAL)
                .inputParameters("{\"breathingRate\":18}")
                .build();
    }

    @Test
    void pending_canTransitionToSubmitted() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.SUBMITTED);
        assertThat(execution.getStartedAt()).isNull();
        assertThat(execution.getCompletedAt()).isNull();
    }

    @Test
    void pending_canTransitionToCancelled() {
        execution.updateStatus(AlgorithmExecutionStatus.CANCELLED);
        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.CANCELLED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void submitted_canTransitionToRunning() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        execution.updateStatus(AlgorithmExecutionStatus.RUNNING);

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.RUNNING);
        assertThat(execution.getStartedAt()).isNotNull();
        assertThat(execution.getCompletedAt()).isNull();
    }

    @Test
    void submitted_canTransitionToCancelled() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        execution.updateStatus(AlgorithmExecutionStatus.CANCELLED);

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.CANCELLED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void submitted_canTransitionToFailed() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        execution.updateStatus(AlgorithmExecutionStatus.FAILED);

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.FAILED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void running_canTransitionToCompleted() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        execution.updateStatus(AlgorithmExecutionStatus.RUNNING);
        execution.updateStatus(AlgorithmExecutionStatus.COMPLETED);

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.COMPLETED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void running_canTransitionToFailed() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        execution.updateStatus(AlgorithmExecutionStatus.RUNNING);
        execution.updateStatus(AlgorithmExecutionStatus.FAILED);

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.FAILED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void running_canTransitionToCancelled() {
        execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        execution.updateStatus(AlgorithmExecutionStatus.RUNNING);
        execution.updateStatus(AlgorithmExecutionStatus.CANCELLED);

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.CANCELLED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void invalidTransition_throwsException() {
        // From PENDING cannot go directly to RUNNING
        assertThatThrownBy(() -> execution.updateStatus(AlgorithmExecutionStatus.RUNNING))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo(DomainExceptionCode.INVALID_EXECUTION_STATUS_TRANSITION);
        // From PENDING cannot go directly to COMPLETED
        assertThatThrownBy(() -> execution.updateStatus(AlgorithmExecutionStatus.COMPLETED))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo(DomainExceptionCode.INVALID_EXECUTION_STATUS_TRANSITION);
    }

    @Test
    void create_usesAnalysisInputParametersWhenCommandInputIsNull() {
        final Analysis analysis = mock(Analysis.class);
        when(analysis.getInputParametersJson()).thenReturn("{\"from\":\"analysis\"}");

        final CreateAlgorithmExecutionCommand command = new CreateAlgorithmExecutionCommand(
                mock(Algorithm.class),
                mock(Patient.class),
                analysis,
                AlgorithmExecutionTriggerType.MANUAL,
                null
        );

        final AlgorithmExecution created = AlgorithmExecution.create(command);

        assertThat(created.getInputParameters()).isEqualTo("{\"from\":\"analysis\"}");
    }
}
