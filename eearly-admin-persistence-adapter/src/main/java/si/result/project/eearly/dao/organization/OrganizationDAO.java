package si.result.project.eearly.dao.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.organization.OrganizationRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface OrganizationDAO extends OrganizationRepository,
        JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {}
