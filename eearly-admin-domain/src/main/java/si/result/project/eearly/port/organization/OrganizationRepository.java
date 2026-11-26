package si.result.project.eearly.port.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.organization.Organization;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository {

    Page<Organization> findAll(Specification<Organization> specification, Pageable pageable);

    Optional<Organization> findById(UUID id);

    Organization save(Organization organization);

}
