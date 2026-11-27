package si.result.project.eearly.mapper.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.department.DepartmentLiteDTO;
import si.result.project.eearly.dto.organization.OrganizationUpsertDTO;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.model.organization.command.UpdateOrganizationCommand;

class OrganizationMapperTest {

    private final OrganizationMapper mapper = new OrganizationMapperImpl();

    private final OrganizationUpsertDTO dto = new OrganizationUpsertDTO(null, "Test Organization",
            List.of(new DepartmentLiteDTO(UUID.randomUUID(), "Test Department")),
            List.of(new CaretakerLiteDTO(UUID.randomUUID(), UUID.randomUUID(), "Test", "Caretaker",
                    "test@example.org", "sl")));

    @Test
    void commandsTakeTheNameAndIgnoreTheDepartmentList() {
        final CreateOrganizationCommand create = mapper.toCreateCommand(dto);
        final UUID id = UUID.randomUUID();
        final UpdateOrganizationCommand update = mapper.toUpdateCommand(id, dto);

        assertThat(create.getName()).isEqualTo("Test Organization");
        assertThat(create.getDepartments()).isNull();
        assertThat(update.getId()).isEqualTo(id);
        assertThat(update.getName()).isEqualTo("Test Organization");
        assertThat(update.getDepartments()).isNull();
    }

    @Test
    void upsertDtoOmitsTheDepartmentList() {
        final Organization organization = Organization.create(new CreateOrganizationCommand("Test Organization", null));

        final OrganizationUpsertDTO result = mapper.toUpsertDTO(organization);

        assertThat(result.name()).isEqualTo("Test Organization");
        assertThat(result.departmentList()).isNull();
        assertThat(result.caretakerList()).isNull();
    }
}
