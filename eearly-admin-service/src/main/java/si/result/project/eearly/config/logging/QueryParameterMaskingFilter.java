package si.result.project.eearly.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hides the values of credential-bearing query parameters ({@code token}, ...) from everything that logs the query
 * string of a request. The request logging of the service ({@code ControllerLoggingAspect}) writes
 * {@code HttpServletRequest#getQueryString()} to the log message and to the {@code url.query} field of every request,
 * and the result callback URL carries its single-use token there ({@code ...?token=...}).
 *
 * <p>Only {@code getQueryString()} is masked ({@code token=***}). The parameter API
 * ({@code getParameter}, {@code @RequestParam}) still has the real values, which is what the security filter and the
 * controllers read. The filter is registered first in the chain (see {@code RequestLogMaskingConfig}), so that the
 * masked request is what every other filter, and the request context the logging aspect reads, holds.
 */
public class QueryParameterMaskingFilter extends OncePerRequestFilter {

    /** Replaces a masked value. */
    public static final String MASK = "***";

    /** Parameter names (lower case) whose values are masked. */
    static final Set<String> SENSITIVE_PARAMETERS = Set.of(
            "token", "access_token", "id_token", "refresh_token", "api_key", "apikey", "password", "secret",
            "client_secret");

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getQueryString() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new MaskedQueryRequest(request), response);
    }

    /** The query string with the values of the sensitive parameters replaced by {@value #MASK}. */
    public static String mask(final String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return queryString;
        }
        final String[] pairs = queryString.split("&", -1);
        final StringBuilder masked = new StringBuilder(queryString.length());
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                masked.append('&');
            }
            final String pair = pairs[i];
            final int equals = pair.indexOf('=');
            if (equals >= 0 && isSensitive(pair.substring(0, equals))) {
                masked.append(pair, 0, equals + 1).append(MASK);
            } else {
                masked.append(pair);
            }
        }
        return masked.toString();
    }

    private static boolean isSensitive(final String rawName) {
        String name = rawName;
        try {
            name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // not decodable: compare as it is
        }
        return SENSITIVE_PARAMETERS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static final class MaskedQueryRequest extends HttpServletRequestWrapper {

        private final String maskedQueryString;

        private MaskedQueryRequest(final HttpServletRequest request) {
            super(request);
            this.maskedQueryString = mask(request.getQueryString());
        }

        @Override
        public String getQueryString() {
            return maskedQueryString;
        }
    }
}
