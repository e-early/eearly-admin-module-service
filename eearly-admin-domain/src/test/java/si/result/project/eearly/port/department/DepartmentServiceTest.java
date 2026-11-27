package si.result.project.eearly.port.department;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.department.command.CreateDepartmentCommand;
import si.result.project.eearly.model.department.command.UpdateDepartmentCommand;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private static Organization organization(final String name) {
        return Organization.create(new CreateOrganizationCommand(name, null));
    }

    @Test
    void shouldCreateDepartmentLinkedToOrganization() {
        // Given
        Organization organization = organization("Test Organization");
        CreateDepartmentCommand command = new CreateDepartmentCommand("Test Department", organization);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Department result = departmentService.create(command);

        // Then
        ArgumentCaptor<Department> saved = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(saved.capture());
        assertSame(result, saved.getValue());
        assertEquals("Test Department", result.getName());
        assertSame(organization, result.getOrganization());
    }

    @Test
    void shouldUpdateNameAndOrganization() {
        // Given
        UUID id = UUID.randomUUID();
        Organization original = organization("Test Organization");
        Organization other = organization("Other Test Organization");
        Department department = Department.create(new CreateDepartmentCommand("Test Department", original));
        when(departmentRepository.findById(id)).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);

        // When
        Department result = departmentService.update(new UpdateDepartmentCommand(id, "Renamed Department", other));

        // Then
        assertSame(department, result);
        assertEquals("Renamed Department", result.getName());
        assertSame(other, result.getOrganization());
        verify(departmentRepository).save(department);
    }

    @Test
    void shouldRejectDepartmentWithoutName() {
        CreateDepartmentCommand command = new CreateDepartmentCommand(null, organization("Test Organization"));

        DomainException exception = assertThrows(DomainException.class, () -> departmentService.create(command));

        assertEquals(DomainExceptionCode.DEPARTMENT_NAME_NULL, exception.getCode());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectDepartmentWithoutOrganization() {
        CreateDepartmentCommand command = new CreateDepartmentCommand("Test Department", null);

        DomainException exception = assertThrows(DomainException.class, () -> departmentService.create(command));

        assertEquals(DomainExceptionCode.DEPARTMENT_ORGANIZATION_NOT_FOUND, exception.getCode());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdateWithoutName() {
        UUID id = UUID.randomUUID();
        Department department = Department.create(
                new CreateDepartmentCommand("Test Department", organization("Test Organization")));
        when(departmentRepository.findById(id)).thenReturn(Optional.of(department));
        UpdateDepartmentCommand command = new UpdateDepartmentCommand(id, null, organization("Test Organization"));

        DomainException exception = assertThrows(DomainException.class, () -> departmentService.update(command));

        assertEquals(DomainExceptionCode.DEPARTMENT_NAME_NULL, exception.getCode());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFound_whenDepartmentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());

        DomainException exception = assertThrows(DomainException.class, () -> departmentService.findById(id));

        assertEquals(DomainExceptionCode.DEPARTMENT_NOT_FOUND, exception.getCode());
    }

    @Test
    void shouldThrowNotFound_whenUpdatedDepartmentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findById(id)).thenReturn(Optional.empty());
        UpdateDepartmentCommand command = new UpdateDepartmentCommand(id, "Test Department", organization("Test Organization"));

        DomainException exception = assertThrows(DomainException.class, () -> departmentService.update(command));

        assertEquals(DomainExceptionCode.DEPARTMENT_NOT_FOUND, exception.getCode());
        verify(departmentRepository, never()).save(any());
    }
}
