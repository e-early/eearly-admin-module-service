package si.result.project.eearly.auth;

/**
 * Roles of the client configured as admin.keycloak.ui-client-id: client roles, mapped to
 * authorities with the ROLE_ prefix by SecurityConfig.
 */
public final class AdminRoles {

    /**
     * The mobile backend's service account, for {@code /api/v1/internal/**}. A client role of the
     * configured client, assigned to the mobile backend's Keycloak client's service account.
     */
    public static final String MOBILE_SERVICE = "MobileService";

    private AdminRoles() {
    }
}
