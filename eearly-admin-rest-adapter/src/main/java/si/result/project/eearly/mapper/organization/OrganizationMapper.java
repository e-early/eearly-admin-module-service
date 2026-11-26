package si.result.project.eearly.mapper.organization;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.organization.OrganizationDTO;
import si.result.project.eearly.dto.organization.OrganizationLiteDTO;
import si.result.project.eearly.dto.organization.OrganizationUpsertDTO;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.model.organization.command.UpdateOrganizationCommand;

import java.util.UUID;

@Mapper
public interface OrganizationMapper {

    OrganizationDTO toDTO(final Organization organization);

    OrganizationLiteDTO toLiteDTO(final Organization organization);

    // Departments are stored through their own endpoints (t_department.id_organization), so the
    // list is neither read from nor written to the organization here.
    @Mapping(target = "departmentList", ignore = true)
    OrganizationUpsertDTO toUpsertDTO(final Organization organization);

    @Mapping(target = "departments", ignore = true)
    CreateOrganizationCommand toCreateCommand(final OrganizationUpsertDTO dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "departments", ignore = true)
    UpdateOrganizationCommand toUpdateCommand(final UUID id, final OrganizationUpsertDTO dto);
}
