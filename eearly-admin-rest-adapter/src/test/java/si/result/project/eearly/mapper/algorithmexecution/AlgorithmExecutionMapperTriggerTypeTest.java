package si.result.project.eearly.mapper.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionUpsertDTO;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.project.eearly.model.patient.Patient;

class AlgorithmExecutionMapperTriggerTypeTest {

    private final AlgorithmExecutionMapper mapper = new AlgorithmExecutionMapperImpl();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"MANUAL", "SCHEDULED"})
    void createCommand_isAlwaysManualRegardlessOfClientValue(final String clientValue) {
        final var dto = new AlgorithmExecutionUpsertDTO(
                UUID.randomUUID(), UUID.randomUUID(), null, clientValue, "[]");

        final CreateAlgorithmExecutionCommand command =
                mapper.toCreateCommand(dto, mock(Algorithm.class), null, mock(Patient.class));

        assertThat(command.triggerType()).isEqualTo(AlgorithmExecutionTriggerType.MANUAL);
    }

    @Test
    void dto_exposesTriggerTypeOfExecution() {
        final AlgorithmExecution execution = AlgorithmExecution.builder()
                .id(UUID.randomUUID())
                .algorithm(mock(Algorithm.class))
                .patient(mock(Patient.class))
                .triggerType(AlgorithmExecutionTriggerType.MANUAL)
                .build();

        assertThat(mapper.toDTO(execution).triggerType()).isEqualTo("MANUAL");
    }
}
