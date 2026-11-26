package si.result.project.eearly.port.caretaker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.department.command.CreateDepartmentCommand;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerServiceTest {

    @Mock
    private CaretakerRepository caretakerRepository;

    @Mock
    private CaretakerClient caretakerClient;

    @InjectMocks
    private CaretakerService caretakerService;

    private final Organization organization = Organization.create(new CreateOrganizationCommand("Test Organization", null));
    private final Department department = Department.create(new CreateDepartmentCommand("Test Department", organization));

    private CreateCaretakerCommand createCommand(final Organization organization, final Department department) {
        return new CreateCaretakerCommand(UUID.randomUUID(), "Test", "Caretaker", "test@example.org", "sl",
                organization, department);
    }

    private UpdateCaretakerCommand updateCommand(final UUID id, final Organization organization,
                                                 final Department department) {
        return new UpdateCaretakerCommand(id, UUID.randomUUID(), "Renamed", "Caretaker", "renamed@example.org", "en",
                organization, department);
    }

    @Test
    void shouldCreateCaretakerWithOrganizationAndDepartmentAndSyncIt() {
        when(caretakerRepository.save(any(Caretaker.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Caretaker result = caretakerService.create(createCommand(organization, department));

        assertSame(organization, result.getOrganization());
        assertSame(department, result.getDepartment());
        assertEquals("Test", result.getFirstName());
        verify(caretakerRepository).save(result);
        verify(caretakerClient).upsertCaretaker(result);
    }

    @Test
    void shouldRejectCaretakerWithoutOrganization() {
        final DomainException exception = assertThrows(DomainException.class,
                () -> caretakerService.create(createCommand(null, department)));

        assertEquals(DomainExceptionCode.CARETAKER_ORGANIZATION_NULL, exception.getCode());
        verify(caretakerRepository, never()).save(any());
        verifyNoInteractions(caretakerClient);
    }

    @Test
    void shouldRejectCaretakerWithoutDepartment() {
        final DomainException exception = assertThrows(DomainException.class,
                () -> caretakerService.create(createCommand(organization, null)));

        assertEquals(DomainExceptionCode.CARETAKER_DEPARTMENT_NULL, exception.getCode());
        verify(caretakerRepository, never()).save(any());
        verifyNoInteractions(caretakerClient);
    }

    @Test
    void shouldUpdateDepartmentAndKeepTheOrganization() {
        final UUID id = UUID.randomUUID();
        final Caretaker caretaker = Caretaker.create(createCommand(organization, department));
        final Organization otherOrganization =
                Organization.create(new CreateOrganizationCommand("Other Test Organization", null));
        final Department otherDepartment =
                Department.create(new CreateDepartmentCommand("Other Test Department", otherOrganization));
        when(caretakerRepository.findById(id)).thenReturn(Optional.of(caretaker));
        when(caretakerRepository.save(caretaker)).thenReturn(caretaker);

        final Caretaker result = caretakerService.update(updateCommand(id, otherOrganization, otherDepartment));

        assertSame(caretaker, result);
        assertSame(otherDepartment, result.getDepartment());
        // The organization of a caretaker is set when it is created and is not changed by an update.
        assertSame(organization, result.getOrganization());
        assertEquals("Renamed", result.getFirstName());
        verify(caretakerClient).upsertCaretaker(caretaker);
    }

    @Test
    void shouldRejectUpdateWithoutDepartmentAndKeepTheCaretaker() {
        final UUID id = UUID.randomUUID();
        final Caretaker caretaker = Caretaker.create(createCommand(organization, department));
        when(caretakerRepository.findById(id)).thenReturn(Optional.of(caretaker));

        final DomainException exception = assertThrows(DomainException.class,
                () -> caretakerService.update(updateCommand(id, organization, null)));

        assertEquals(DomainExceptionCode.CARETAKER_DEPARTMENT_NULL, exception.getCode());
        assertSame(department, caretaker.getDepartment());
        verify(caretakerRepository, never()).save(any());
        verifyNoInteractions(caretakerClient);
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingAnUnknownCaretaker() {
        final UUID id = UUID.randomUUID();
        when(caretakerRepository.findById(id)).thenReturn(Optional.empty());

        final DomainException exception = assertThrows(DomainException.class,
                () -> caretakerService.update(updateCommand(id, organization, department)));

        assertEquals(DomainExceptionCode.CARETAKER_NOT_FOUND, exception.getCode());
        verify(caretakerRepository, never()).save(any());
    }
}
