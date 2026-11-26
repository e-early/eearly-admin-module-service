package si.result.project.eearly.facade.caretaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerServiceFacadeTest {

    @Mock
    private CaretakerService caretakerService;
    @Mock
    private CaretakerMapper caretakerMapper;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private DepartmentService departmentService;

    @InjectMocks
    private CaretakerServiceFacade facade;

    private UUID organizationId;
    private UUID departmentId;
    private Organization organization;
    private Department department;
    private CaretakerUpsertDTO request;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        organization = mock(Organization.class);
        department = mock(Department.class);
        request = new CaretakerUpsertDTO(null, UUID.randomUUID(), "Test", "Caretaker", "test@example.org", "sl",
                organizationId, departmentId);
    }

    @Test
    void createResolvesTheOrganizationAndDepartmentAndPassesThemOn() {
        final CreateCaretakerCommand command = mock(CreateCaretakerCommand.class);
        final Caretaker caretaker = mock(Caretaker.class);
        final CaretakerDTO dto = mock(CaretakerDTO.class);
        when(organizationService.findById(organizationId)).thenReturn(organization);
        when(departmentService.findById(departmentId)).thenReturn(department);
        when(caretakerMapper.toCreateCommand(request, organization, department)).thenReturn(command);
        when(caretakerService.create(command)).thenReturn(caretaker);
        when(caretakerMapper.toDTO(caretaker)).thenReturn(dto);

        assertSame(dto, facade.create(request));
    }

    @Test
    void updateResolvesTheOrganizationAndDepartmentAndPassesThemOn() {
        final UUID id = UUID.randomUUID();
        final UpdateCaretakerCommand command = mock(UpdateCaretakerCommand.class);
        final Caretaker caretaker = mock(Caretaker.class);
        final CaretakerDTO dto = mock(CaretakerDTO.class);
        when(organizationService.findById(organizationId)).thenReturn(organization);
        when(departmentService.findById(departmentId)).thenReturn(department);
        when(caretakerMapper.toUpdateCommand(id, request, organization, department)).thenReturn(command);
        when(caretakerService.update(command)).thenReturn(caretaker);
        when(caretakerMapper.toDTO(caretaker)).thenReturn(dto);

        assertSame(dto, facade.update(id, request));
    }

    @Test
    void missingIdsAreLeftNullForTheDomainToReject() {
        final CaretakerUpsertDTO withoutIds = new CaretakerUpsertDTO(null, null, "Test", "Caretaker",
                "test@example.org", "sl", null, null);
        final CreateCaretakerCommand command = mock(CreateCaretakerCommand.class);
        when(caretakerMapper.toCreateCommand(withoutIds, null, null)).thenReturn(command);
        when(caretakerService.create(command)).thenThrow(
                new DomainException(DomainExceptionCode.CARETAKER_ORGANIZATION_NULL));

        final DomainException exception = assertThrows(DomainException.class, () -> facade.create(withoutIds));

        assertEquals(DomainExceptionCode.CARETAKER_ORGANIZATION_NULL, exception.getCode());
        verifyNoInteractions(organizationService, departmentService);
    }

    @Test
    void unknownOrganizationIsNotFoundAndNothingIsSaved() {
        when(organizationService.findById(organizationId))
                .thenThrow(new DomainException(DomainExceptionCode.ORGANIZATION_NOT_FOUND));

        final DomainException exception = assertThrows(DomainException.class, () -> facade.create(request));

        assertEquals(DomainExceptionCode.ORGANIZATION_NOT_FOUND, exception.getCode());
        verify(caretakerService, never()).create(any());
    }

    @Test
    void unknownDepartmentIsNotFoundAndNothingIsSaved() {
        when(organizationService.findById(organizationId)).thenReturn(organization);
        when(departmentService.findById(departmentId))
                .thenThrow(new DomainException(DomainExceptionCode.DEPARTMENT_NOT_FOUND));

        final DomainException exception = assertThrows(DomainException.class,
                () -> facade.update(UUID.randomUUID(), request));

        assertEquals(DomainExceptionCode.DEPARTMENT_NOT_FOUND, exception.getCode());
        verify(caretakerService, never()).update(any());
        verifyNoInteractions(caretakerMapper);
    }
}
