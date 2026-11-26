package si.result.project.eearly.mapper.department;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.department.DepartmentDTO;
import si.result.project.eearly.dto.department.DepartmentLiteDTO;
import si.result.project.eearly.dto.department.DepartmentUpsertDTO;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.department.command.CreateDepartmentCommand;
import si.result.project.eearly.model.department.command.UpdateDepartmentCommand;
import si.result.project.eearly.model.organization.Organization;

import java.util.UUID;

@Mapper
public interface DepartmentMapper {

    DepartmentDTO toDTO(final Department department);

    DepartmentLiteDTO toLiteDTO(final Department department);

    @Mapping(target = "organizationId", source = "organization.id")
    DepartmentUpsertDTO toUpsertDTO(final Department department);

    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "organization", source = "organization")
    CreateDepartmentCommand toCreateCommand(final DepartmentUpsertDTO dto, final Organization organization);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "organization", source = "organization")
    UpdateDepartmentCommand toUpdateCommand(final UUID id, final DepartmentUpsertDTO dto, final Organization organization);
}
