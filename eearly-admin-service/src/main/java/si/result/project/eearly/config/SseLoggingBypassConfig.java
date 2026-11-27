package si.result.project.eearly.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import si.result.logging.spring.boot.ResponseContentLogger;

import java.io.IOException;

/**
 * Prevents the HTTP response logging filter from wrapping SSE (text/event-stream) responses.
 *
 * ResponseContentLogger wraps every response in ContentCachingResponseWrapper, reads the buffered
 * body after chain.doFilter() returns, and calls copyBodyToResponse() - which sets a fixed
 * Content-Length header. For SSE this holds back the events and ends the connection after the first one.
 *
 * Only active where the response logger is (logging.loggers.method.enabled), as the logger bean itself.
 */
@Configuration
@ConditionalOnProperty(name = "logging.loggers.method.enabled")
public class SseLoggingBypassConfig {

    /** Path of the notification stream; its requests are never wrapped, whatever their Accept header. */
    static final String NOTIFICATION_STREAM_PATH = "/api/v1/notifications/stream";

    /**
     * Disables Spring Boot's auto-registration of ResponseContentLogger so our wrapper is
     * the only entry in the filter chain.
     */
    @Bean
    public FilterRegistrationBean<ResponseContentLogger> disableAutoResponseContentLogger(ResponseContentLogger responseContentLogger) {
        FilterRegistrationBean<ResponseContentLogger> reg = new FilterRegistrationBean<>(responseContentLogger);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * Re-registers ResponseContentLogger for all non-SSE requests. SSE requests (Accept:
     * text/event-stream, or the notification stream path itself, since a client may send a
     * different Accept header) are passed directly to the filter chain without wrapping.
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> sseAwareLoggingFilter(ResponseContentLogger responseContentLogger) {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                if (isEventStreamRequest(request)) {
                    filterChain.doFilter(request, response);
                } else {
                    responseContentLogger.doFilter(request, response, filterChain);
                }
            }
        });
        reg.addUrlPatterns("/*");
        return reg;
    }

    private static boolean isEventStreamRequest(HttpServletRequest request) {
        final String accept = request.getHeader("Accept");
        return (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || request.getRequestURI().endsWith(NOTIFICATION_STREAM_PATH);
    }
}
