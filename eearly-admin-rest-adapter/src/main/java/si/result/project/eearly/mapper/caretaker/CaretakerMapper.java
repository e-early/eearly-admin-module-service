package si.result.project.eearly.mapper.caretaker;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;

import java.util.UUID;

@Mapper
public interface CaretakerMapper {

    CaretakerDTO toDTO(final Caretaker caretaker);

    CaretakerLiteDTO toLiteDTO(final Caretaker caretaker);

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "departmentId", source = "department.id")
    CaretakerUpsertDTO toUpsertDTO(final Caretaker caretaker);

    @Mapping(target = "keycloakId", source = "dto.keycloakId")
    @Mapping(target = "firstName", source = "dto.firstName")
    @Mapping(target = "lastName", source = "dto.lastName")
    @Mapping(target = "email", source = "dto.email")
    @Mapping(target = "language", source = "dto.language")
    @Mapping(target = "organization", source = "organization")
    @Mapping(target = "department", source = "department")
    CreateCaretakerCommand toCreateCommand(final CaretakerUpsertDTO dto,
                                           final Organization organization,
                                           final Department department);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "keycloakId", source = "dto.keycloakId")
    @Mapping(target = "firstName", source = "dto.firstName")
    @Mapping(target = "lastName", source = "dto.lastName")
    @Mapping(target = "email", source = "dto.email")
    @Mapping(target = "language", source = "dto.language")
    @Mapping(target = "organization", source = "organization")
    @Mapping(target = "department", source = "department")
    UpdateCaretakerCommand toUpdateCommand(final UUID id, final CaretakerUpsertDTO dto,
                                           final Organization organization, final Department department);
}
