package si.result.project.eearly.mapper.healthcondition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import si.result.project.eearly.dto.patient.PatientUpsertDTO;
import si.result.project.eearly.mapper.algorithm.AlgorithmMapper;
import si.result.project.eearly.mapper.patient.PatientMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.model.healthcondition.command.CreateHealthConditionCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.command.CreatePatientCommand;

/** The health conditions of patients and algorithms as they pass through the mappers. */
class HealthConditionRelationMappingTest {

    private final PatientMapper patientMapper = Mappers.getMapper(PatientMapper.class);
    private final AlgorithmMapper algorithmMapper = Mappers.getMapper(AlgorithmMapper.class);

    private final List<HealthCondition> healthConditions = List.of(
            HealthCondition.create(new CreateHealthConditionCommand("First Condition")),
            HealthCondition.create(new CreateHealthConditionCommand("Second Condition")));

    private final PatientUpsertDTO patientDto = new PatientUpsertDTO(
            null, "Ana", "Novak", LocalDate.of(1990, 1, 1), GenderType.FEMALE, "123456789", "Street", "City",
            "State", 1000, "Country", "ana@example.org", "+38640000000", null, null, null, List.of(), null);

    @Test
    void patientCommandsCarryTheResolvedHealthConditions() {
        final var create = patientMapper.toCreateCommand(patientDto, List.of(), List.of(), healthConditions, List.of());
        final var update = patientMapper.toUpdateCommand(
                UUID.randomUUID(), patientDto, List.of(), List.of(), healthConditions, List.of());

        assertThat(create.getHealthConditions()).containsExactlyElementsOf(healthConditions);
        assertThat(update.getHealthConditions()).containsExactlyElementsOf(healthConditions);
    }

    @Test
    void patientIsMappedWithItsHealthConditions() {
        final Patient patient = Patient.create(new CreatePatientCommand(
                "Ana", "Novak", LocalDate.of(1990, 1, 1), GenderType.FEMALE, "123456789", "Street", "City",
                "State", 1000, "Country", "ana@example.org", "+38640000000", null,
                List.of(), List.of(), healthConditions, List.of()));

        final var dto = patientMapper.toDTO(patient);

        assertThat(dto.healthConditions()).extracting("name")
                .containsExactly("First Condition", "Second Condition");
    }

    @Test
    void algorithmCommandAndDtoCarryTheHealthConditions() {
        final var upsert = algorithmMapper.toUpsertDTO(Algorithm.builder().name("Test Algorithm").build());
        final var command = algorithmMapper.toCreateCommand(upsert, List.of(), List.of(), healthConditions);
        final Algorithm algorithm = Algorithm.create(command);

        final var dto = algorithmMapper.toDTO(algorithm);

        assertThat(command.healthConditions()).containsExactlyElementsOf(healthConditions);
        assertThat(dto.healthConditions()).extracting("name")
                .containsExactly("First Condition", "Second Condition");
    }
}
