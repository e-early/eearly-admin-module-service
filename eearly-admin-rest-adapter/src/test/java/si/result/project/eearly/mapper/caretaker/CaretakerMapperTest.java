package si.result.project.eearly.mapper.caretaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.dto.department.DepartmentDTO;
import si.result.project.eearly.dto.organization.OrganizationDTO;
import si.result.project.eearly.mapper.department.DepartmentMapperImpl;
import si.result.project.eearly.mapper.organization.OrganizationMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;

class CaretakerMapperTest {

    private final CaretakerMapper mapper = new CaretakerMapperImpl();

    private final UUID organizationId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final Organization organization = mock(Organization.class);
    private final Department department = mock(Department.class);

    private final CaretakerUpsertDTO dto = new CaretakerUpsertDTO(null, UUID.randomUUID(), "Test", "Caretaker",
            "test@example.org", "sl", organizationId, departmentId);

    private Caretaker caretaker() {
        when(organization.getId()).thenReturn(organizationId);
        when(organization.getName()).thenReturn("Test Organization");
        when(department.getId()).thenReturn(departmentId);
        when(department.getName()).thenReturn("Test Department");
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(UUID.randomUUID());
        when(caretaker.getFirstName()).thenReturn("Test");
        when(caretaker.getLastName()).thenReturn("Caretaker");
        when(caretaker.getOrganization()).thenReturn(organization);
        when(caretaker.getDepartment()).thenReturn(department);
        return caretaker;
    }

    @Test
    void dtoCarriesTheOrganizationAndDepartment() {
        final CaretakerDTO result = mapper.toDTO(caretaker());

        assertThat(result.firstName()).isEqualTo("Test");
        assertThat(result.organization().id()).isEqualTo(organizationId);
        assertThat(result.organization().name()).isEqualTo("Test Organization");
        assertThat(result.department().id()).isEqualTo(departmentId);
        assertThat(result.department().name()).isEqualTo("Test Department");
    }

    @Test
    void upsertDtoCarriesTheOrganizationAndDepartmentIds() {
        final CaretakerUpsertDTO result = mapper.toUpsertDTO(caretaker());

        assertThat(result.organizationId()).isEqualTo(organizationId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
    }

    @Test
    void createCommandTakesTheResolvedOrganizationAndDepartment() {
        final CreateCaretakerCommand result = mapper.toCreateCommand(dto, organization, department);

        assertThat(result.getKeycloakId()).isEqualTo(dto.keycloakId());
        assertThat(result.getFirstName()).isEqualTo("Test");
        assertThat(result.getLastName()).isEqualTo("Caretaker");
        assertThat(result.getEmail()).isEqualTo("test@example.org");
        assertThat(result.getLanguage()).isEqualTo("sl");
        assertThat(result.getOrganization()).isSameAs(organization);
        assertThat(result.getDepartment()).isSameAs(department);
    }

    @Test
    void updateCommandTakesTheIdAndTheResolvedOrganizationAndDepartment() {
        final UUID id = UUID.randomUUID();

        final UpdateCaretakerCommand result = mapper.toUpdateCommand(id, dto, organization, department);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getEmail()).isEqualTo("test@example.org");
        assertThat(result.getOrganization()).isSameAs(organization);
        assertThat(result.getDepartment()).isSameAs(department);
    }

    @Test
    void commandsKeepMissingOrganizationAndDepartmentNullForTheDomainToReject() {
        final CreateCaretakerCommand result = mapper.toCreateCommand(dto, null, null);

        assertThat(result.getOrganization()).isNull();
        assertThat(result.getDepartment()).isNull();
    }

    @Test
    void organizationAndDepartmentDtosListTheirCaretakers() {
        final Caretaker caretaker = caretaker();
        when(organization.getCaretakers()).thenReturn(List.of(caretaker));
        when(department.getCaretakers()).thenReturn(List.of(caretaker));

        final OrganizationDTO organizationDTO = new OrganizationMapperImpl().toDTO(organization);
        final DepartmentDTO departmentDTO = new DepartmentMapperImpl().toDTO(department);

        assertThat(organizationDTO.caretakers()).singleElement()
                .satisfies(c -> assertThat(c.firstName()).isEqualTo("Test"));
        assertThat(departmentDTO.caretakers()).singleElement()
                .satisfies(c -> assertThat(c.lastName()).isEqualTo("Caretaker"));
    }
}
