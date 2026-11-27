package si.result.project.eearly.mapper.healthcondition;

import org.mapstruct.Mapper;
import si.result.project.eearly.dto.healthcondition.HealthConditionDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionLiteDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionMinimalDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionUpsertDTO;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.model.healthcondition.command.CreateHealthConditionCommand;

@Mapper
public interface HealthConditionMapper {

    HealthConditionDTO toDTO(HealthCondition healthCondition);

    HealthConditionLiteDTO toLiteDTO(HealthCondition healthCondition);

    HealthConditionMinimalDTO toMinimalDTO(HealthCondition healthCondition);

    CreateHealthConditionCommand toCreateCommand(HealthConditionUpsertDTO dto);

}
