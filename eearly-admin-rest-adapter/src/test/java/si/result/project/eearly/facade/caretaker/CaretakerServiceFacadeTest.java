package si.result.project.eearly.facade.caretaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerServiceFacadeTest {

    private static final Set<GrantedAuthority> ADMINISTRATOR = Set.of(new SimpleGrantedAuthority("ROLE_Administrator"));
    private static final Set<GrantedAuthority> CLINICIAN = Set.of(new SimpleGrantedAuthority("ROLE_Clinician"));

    @Mock
    private CaretakerService caretakerService;
    @Mock
    private CaretakerMapper caretakerMapper;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private DepartmentService departmentService;
    @Mock
    private AuthenticationProvider authenticationProvider;

    @InjectMocks
    private CaretakerServiceFacade facade;

    private UUID organizationId;
    private UUID departmentId;
    private Organization organization;
    private Department department;
    private CaretakerUpsertDTO request;
    private UUID callerSubject;
    private UUID caretakerId;
    private UUID caretakerKeycloakId;
    private Caretaker existing;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        organization = mock(Organization.class);
        department = mock(Department.class);
        request = new CaretakerUpsertDTO(null, UUID.randomUUID(), "Test", "Caretaker", "test@example.org", "sl",
                organizationId, departmentId);
        callerSubject = UUID.randomUUID();
        caretakerId = UUID.randomUUID();
        caretakerKeycloakId = UUID.randomUUID();
        existing = mock(Caretaker.class);
        lenient().when(existing.getId()).thenReturn(caretakerId);
        lenient().when(existing.getKeycloakId()).thenReturn(caretakerKeycloakId);
        lenient().when(authenticationProvider.getId()).thenReturn(callerSubject.toString());
    }

    private void callerIsTheCaretaker() {
        when(authenticationProvider.getId()).thenReturn(caretakerKeycloakId.toString());
        lenient().when(authenticationProvider.getAuthorities()).thenReturn(CLINICIAN);
    }

    private void callerHas(final Set<GrantedAuthority> authorities) {
        when(authenticationProvider.getAuthorities()).thenReturn(authorities);
    }

    private static void assertCaretakerNotFound(final DomainException exception) {
        assertEquals(DomainExceptionCode.CARETAKER_NOT_FOUND, exception.getCode());
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
        callerHas(ADMINISTRATOR);
        when(caretakerService.findById(id)).thenReturn(existing);
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
        callerHas(ADMINISTRATOR);
        when(caretakerService.findById(caretakerId)).thenReturn(existing);
        when(organizationService.findById(organizationId)).thenReturn(organization);
        when(departmentService.findById(departmentId))
                .thenThrow(new DomainException(DomainExceptionCode.DEPARTMENT_NOT_FOUND));

        final DomainException exception = assertThrows(DomainException.class,
                () -> facade.update(caretakerId, request));

        assertEquals(DomainExceptionCode.DEPARTMENT_NOT_FOUND, exception.getCode());
        verify(caretakerService, never()).update(any());
        verifyNoInteractions(caretakerMapper);
    }

    @Test
    @DisplayName("A caretaker gets their own record and updates it")
    void selfMayReadAndUpdate() {
        final CaretakerDTO dto = mock(CaretakerDTO.class);
        final UpdateCaretakerCommand command = mock(UpdateCaretakerCommand.class);
        callerIsTheCaretaker();
        when(caretakerService.findById(caretakerId)).thenReturn(existing);
        when(caretakerService.findByKeycloakId(caretakerKeycloakId)).thenReturn(existing);
        when(organizationService.findById(organizationId)).thenReturn(organization);
        when(departmentService.findById(departmentId)).thenReturn(department);
        when(caretakerMapper.toUpdateCommand(caretakerId, request, organization, department)).thenReturn(command);
        when(caretakerService.update(command)).thenReturn(existing);
        when(caretakerMapper.toDTO(existing)).thenReturn(dto);

        assertSame(dto, facade.getById(caretakerId));
        assertSame(dto, facade.getByKeycloakId(caretakerKeycloakId));
        assertSame(dto, facade.update(caretakerId, request));
    }

    @Test
    @DisplayName("An administrator gets and updates any caretaker")
    void administratorMayReadAndUpdateAny() {
        final CaretakerDTO dto = mock(CaretakerDTO.class);
        callerHas(ADMINISTRATOR);
        when(caretakerService.findById(caretakerId)).thenReturn(existing);
        when(caretakerService.findByKeycloakId(caretakerKeycloakId)).thenReturn(existing);
        when(caretakerMapper.toDTO(existing)).thenReturn(dto);

        assertSame(dto, facade.getById(caretakerId));
        assertSame(dto, facade.getByKeycloakId(caretakerKeycloakId));
    }

    @Test
    @DisplayName("Another caretaker gets CARETAKER_NOT_FOUND on read and update, and nothing is changed")
    void anotherCaretakerIsNotFound() {
        callerHas(CLINICIAN);
        when(caretakerService.findById(caretakerId)).thenReturn(existing);
        when(caretakerService.findByKeycloakId(caretakerKeycloakId)).thenReturn(existing);

        assertCaretakerNotFound(assertThrows(DomainException.class, () -> facade.getById(caretakerId)));
        assertCaretakerNotFound(assertThrows(DomainException.class,
                () -> facade.getByKeycloakId(caretakerKeycloakId)));
        assertCaretakerNotFound(assertThrows(DomainException.class, () -> facade.update(caretakerId, request)));

        verify(caretakerService, never()).update(any());
        verifyNoInteractions(caretakerMapper, organizationService, departmentService);
    }

    @Test
    @DisplayName("A caller whose subject differs only in case or a caretaker without a Keycloak id is not self")
    void selfIsAnExactMatchOfTheSubject() {
        callerHas(CLINICIAN);
        when(authenticationProvider.getId()).thenReturn(caretakerKeycloakId.toString().toUpperCase());
        when(caretakerService.findById(caretakerId)).thenReturn(existing);
        assertCaretakerNotFound(assertThrows(DomainException.class, () -> facade.getById(caretakerId)));

        final Caretaker unlinked = mock(Caretaker.class);
        when(unlinked.getKeycloakId()).thenReturn(null);
        when(authenticationProvider.getId()).thenReturn("null");
        when(caretakerService.findById(caretakerId)).thenReturn(unlinked);
        assertCaretakerNotFound(assertThrows(DomainException.class, () -> facade.getById(caretakerId)));
    }

    @Test
    @DisplayName("An unknown caretaker is CARETAKER_NOT_FOUND for everyone")
    void unknownCaretakerIsNotFound() {
        when(caretakerService.findById(caretakerId))
                .thenThrow(new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND));

        assertCaretakerNotFound(assertThrows(DomainException.class, () -> facade.update(caretakerId, request)));
        verifyNoInteractions(authenticationProvider);
    }
}
