package si.result.project.eearly.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService.VerifiedCallbackToken;

/**
 * Lets a webhook-style algorithm provider call the two result endpoints with the single-use callback token from the
 * {@code token} query parameter of its callback URL instead of a Keycloak JWT.
 *
 * <p>It only acts when <em>all</em> of these hold: callback tokens are enabled, the request is a POST to
 * {@code /api/v1/analyses/{id}/result} or {@code /api/v1/analyses/results}, it carries no {@code Authorization}
 * header (a request with a JWT, valid or not, goes the JWT way, exactly as without this filter), and it has exactly
 * one {@code token} parameter. A token with a good format, signature and expiry then authenticates the request, and
 * the token is passed to the controller in a request attribute. A token that is not good authenticates nothing, so
 * the request meets the authorization rule unauthenticated and is answered 401 before it reaches any controller.
 *
 * <p>This filter does not use the token up and does not check which analysis it was issued for: that happens in the
 * transaction that processes the result (see {@code AnalysisServiceFacade#updateResult}).
 */
@Slf4j
public class CallbackTokenAuthenticationFilter extends OncePerRequestFilter {

    /** Name of the query parameter that carries the token. */
    public static final String TOKEN_PARAMETER = "token";

    private static final RequestMatcher RESULT_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/analyses/*/result"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/analyses/results"));

    private final CallbackTokenService callbackTokenService;

    public CallbackTokenAuthenticationFilter(final CallbackTokenService callbackTokenService) {
        this.callbackTokenService = callbackTokenService;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if (callbackTokenService.isEnabled()
                && RESULT_ENDPOINTS.matches(request)
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(final HttpServletRequest request) {
        final String token = singleTokenParameter(request);
        if (token == null) {
            return;
        }
        try {
            final VerifiedCallbackToken verified = callbackTokenService.verify(token);
            final SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new CallbackTokenAuthentication(verified.executionId()));
            SecurityContextHolder.setContext(context);
            request.setAttribute(CallbackTokenService.VERIFIED_TOKEN_ATTRIBUTE, token);
        } catch (CallbackTokenRejectedException exception) {
            // Never the token or the URL, only why.
            log.warn("Result callback refused: callback token rejected ({})", exception.getReason());
        }
    }

    /**
     * The value of the only {@code token} parameter, or {@code null} if there is none or more than one. Read through
     * the parameter API, not from the query string: the query string of the request is masked for logging (see
     * {@code QueryParameterMaskingFilter}).
     */
    private static String singleTokenParameter(final HttpServletRequest request) {
        final String[] values = request.getParameterValues(TOKEN_PARAMETER);
        return values != null && values.length == 1 ? values[0] : null;
    }
}
