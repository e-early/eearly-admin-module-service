package si.result.project.eearly.mapper.healthcondition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.dto.healthcondition.HealthConditionDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionLiteDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionMinimalDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionUpsertDTO;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.model.healthcondition.command.CreateHealthConditionCommand;

class HealthConditionMapperTest {

    private final HealthConditionMapper mapper = new HealthConditionMapperImpl();

    private final HealthCondition healthCondition =
            HealthCondition.create(new CreateHealthConditionCommand("Test Condition"));

    @Test
    void createCommandTakesTheNameAndIgnoresTheId() {
        final CreateHealthConditionCommand command =
                mapper.toCreateCommand(new HealthConditionUpsertDTO(UUID.randomUUID(), "Test Condition"));

        assertThat(command.getName()).isEqualTo("Test Condition");
    }

    @Test
    void entityIsMappedToTheFullLiteAndMinimalDtos() {
        final HealthConditionDTO dto = mapper.toDTO(healthCondition);
        final HealthConditionLiteDTO lite = mapper.toLiteDTO(healthCondition);
        final HealthConditionMinimalDTO minimal = mapper.toMinimalDTO(healthCondition);

        assertThat(dto.name()).isEqualTo("Test Condition");
        assertThat(lite.name()).isEqualTo("Test Condition");
        assertThat(minimal.id()).isEqualTo(healthCondition.getId());
    }
}
