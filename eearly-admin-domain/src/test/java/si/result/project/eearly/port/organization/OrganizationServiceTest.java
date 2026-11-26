package si.result.project.eearly.port.organization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.model.organization.command.UpdateOrganizationCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationClient organizationClient;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void shouldCallFacilityClient_whenOrganizationIsCreated() {
        // Given
        CreateOrganizationCommand command = new CreateOrganizationCommand("Test Organization");
        Organization organization = mock(Organization.class);
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization);

        // When
        Organization result = organizationService.create(command);

        // Then
        assertEquals(organization, result);
        ArgumentCaptor<Organization> saved = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(saved.capture());
        assertEquals("Test Organization", saved.getValue().getName());
        verify(organizationClient).upsertFacility(organization);
    }

    @Test
    void shouldCallFacilityClient_whenOrganizationIsUpdated() {
        // Given
        UUID id = UUID.randomUUID();
        UpdateOrganizationCommand command = new UpdateOrganizationCommand(id, "Renamed Organization");
        Organization organization = Organization.create(new CreateOrganizationCommand("Test Organization"));
        when(organizationRepository.findById(id)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(organization)).thenReturn(organization);

        // When
        Organization result = organizationService.update(command);

        // Then
        assertEquals(organization, result);
        assertEquals("Renamed Organization", result.getName());
        verify(organizationRepository).save(organization);
        verify(organizationClient).upsertFacility(organization);
    }

    @Test
    void shouldRejectOrganizationWithoutName_andNotSyncFacility() {
        CreateOrganizationCommand command = new CreateOrganizationCommand(null);

        DomainException exception = assertThrows(DomainException.class, () -> organizationService.create(command));

        assertEquals(DomainExceptionCode.ORGANIZATION_NAME_NULL, exception.getCode());
        verify(organizationRepository, never()).save(any());
        verifyNoInteractions(organizationClient);
    }

    @Test
    void shouldThrowNotFound_whenOrganizationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        DomainException exception = assertThrows(DomainException.class, () -> organizationService.findById(id));

        assertEquals(DomainExceptionCode.ORGANIZATION_NOT_FOUND, exception.getCode());
    }

    @Test
    void shouldNotSyncFacility_whenUpdatedOrganizationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DomainException.class,
                () -> organizationService.update(new UpdateOrganizationCommand(id, "Test Organization")));

        verify(organizationRepository, never()).save(any());
        verifyNoInteractions(organizationClient);
    }
}
