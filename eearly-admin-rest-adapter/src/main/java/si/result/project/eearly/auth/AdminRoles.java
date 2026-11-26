package si.result.project.eearly.auth;

import si.result.spring.boot.bricks.auth.AuthenticationProvider;

/**
 * Roles of the client configured as admin.keycloak.ui-client-id: client roles, mapped to
 * authorities with the ROLE_ prefix by SecurityConfig.
 */
public final class AdminRoles {

    /** Administration (organizations, departments, caretakers). */
    public static final String ADMINISTRATOR = "Administrator";

    /**
     * The mobile backend's service account, for {@code /api/v1/internal/**}. A client role of the
     * configured client, assigned to the mobile backend's Keycloak client's service account.
     */
    public static final String MOBILE_SERVICE = "MobileService";

    private static final String ADMINISTRATOR_AUTHORITY = "ROLE_" + ADMINISTRATOR;

    private AdminRoles() {
    }

    public static boolean isAdministrator(final AuthenticationProvider authenticationProvider) {
        return authenticationProvider.getAuthorities().stream()
                .anyMatch(authority -> ADMINISTRATOR_AUTHORITY.equals(authority.getAuthority()));
    }
}
