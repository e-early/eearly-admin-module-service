package si.result.project.eearly.config.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authentication of a request that presented a valid callback token instead of a JWT. It is only ever created by
 * {@link CallbackTokenAuthenticationFilter}, for the two result endpoints, and holds no credential: the token itself
 * is not kept here.
 */
public class CallbackTokenAuthentication extends AbstractAuthenticationToken {

    public static final String AUTHORITY = "ROLE_ALGORITHM_CALLBACK";

    private final UUID executionId;

    public CallbackTokenAuthentication(final UUID executionId) {
        super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
        this.executionId = executionId;
        setAuthenticated(true);
    }

    /** The algorithm execution the token was issued for. */
    public UUID getExecutionId() {
        return executionId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return "algorithm-execution:" + executionId;
    }
}
