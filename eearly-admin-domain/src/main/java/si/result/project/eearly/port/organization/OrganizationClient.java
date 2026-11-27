package si.result.project.eearly.port.organization;

import si.result.project.eearly.model.organization.Organization;

/**
 * Keeps the mobile backend's facility record of an organization in sync.
 */
public interface OrganizationClient {
    void upsertFacility(Organization organization);
}
