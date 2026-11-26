package si.result.project.eearly.port.organization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.model.organization.command.UpdateOrganizationCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationClient organizationClient;

    public Organization findById(final UUID id) {
        return organizationRepository.findById(id).orElseThrow(() -> {
            log.error("Organization with ID {} not found", id);
            return new DomainException(DomainExceptionCode.ORGANIZATION_NOT_FOUND);
        });
    }

    public Page<Organization> findAll(final Specification<Organization> specification, final Pageable pageable) {
        return organizationRepository.findAll(specification, pageable);
    }

    public Organization create(final CreateOrganizationCommand command) {
        log.info("Creating organization: {}", command);
        final var organization = Organization.create(command);
        final var savedOrganization = organizationRepository.save(organization);
        log.debug("Organization created with ID: {}", savedOrganization.getId());
        organizationClient.upsertFacility(savedOrganization);
        return savedOrganization;
    }

    public Organization update(final UpdateOrganizationCommand command) {
        log.info("Updating organization: {}", command);
        final var organization = findById(command.getId());
        organization.update(command);
        final var savedOrganization = organizationRepository.save(organization);
        log.debug("Organization updated with ID: {}", savedOrganization.getId());
        organizationClient.upsertFacility(savedOrganization);
        return savedOrganization;
    }
}
