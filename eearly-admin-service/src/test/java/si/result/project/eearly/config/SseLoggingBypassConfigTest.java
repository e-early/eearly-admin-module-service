package si.result.project.eearly.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import si.result.logging.appender.info.ServiceProperties;
import si.result.logging.spring.boot.ResponseContentLogger;

/**
 * The response logger wraps responses in a caching wrapper, which holds back a stream. The bypass keeps it away
 * from event streams and leaves every other response to the logger.
 */
class SseLoggingBypassConfigTest {

    private final ResponseContentLogger responseLogger =
            new ResponseContentLogger(new ServiceProperties("eearly-admin-service", "test", "TEST", true));
    private final SseLoggingBypassConfig config = new SseLoggingBypassConfig();

    /** The response the controller would write to, as seen through the filter chain. */
    private ServletResponse responseSeenByTheChain(final String uri, final String accept) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final AtomicReference<ServletResponse> seen = new AtomicReference<>();
        final Filter filter = config.sseAwareLoggingFilter(responseLogger).getFilter();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(final ServletRequest req, final ServletResponse res) {
                seen.set(res);
            }
        });
        assertThat(seen.get()).isNotNull();
        if (!(seen.get() instanceof ContentCachingResponseWrapper)) {
            assertThat(seen.get()).isSameAs(response);
        }
        return seen.get();
    }

    @Test
    @DisplayName("A request that accepts text/event-stream is not wrapped")
    void eventStreamAccept_isNotWrapped() throws Exception {
        assertThat(responseSeenByTheChain("/api/v1/anything", "text/event-stream"))
                .isNotInstanceOf(ContentCachingResponseWrapper.class);
    }

    @Test
    @DisplayName("The notification stream is not wrapped, whatever its Accept header")
    void notificationStream_isNotWrapped() throws Exception {
        for (final String accept : new String[] {null, "*/*", "application/json, */*"}) {
            assertThat(responseSeenByTheChain("/api/v1/notifications/stream", accept))
                    .isNotInstanceOf(ContentCachingResponseWrapper.class);
            assertThat(responseSeenByTheChain("/admin/api/v1/notifications/stream", accept))
                    .isNotInstanceOf(ContentCachingResponseWrapper.class);
        }
    }

    @Test
    @DisplayName("Other requests are wrapped by the response logger as before")
    void otherRequests_areWrapped() throws Exception {
        assertThat(responseSeenByTheChain("/api/v1/notifications", "application/json"))
                .isInstanceOf(ContentCachingResponseWrapper.class);
        assertThat(responseSeenByTheChain("/api/v1/notifications/unread", null))
                .isInstanceOf(ContentCachingResponseWrapper.class);
    }

    @Test
    @DisplayName("The automatic registration of the response logger is off and the aware filter covers every path")
    void registrations() {
        final FilterRegistrationBean<ResponseContentLogger> automatic = config.disableAutoResponseContentLogger(responseLogger);
        final FilterRegistrationBean<OncePerRequestFilter> aware = config.sseAwareLoggingFilter(responseLogger);

        assertThat(automatic.isEnabled()).isFalse();
        assertThat(aware.isEnabled()).isTrue();
        assertThat(aware.getUrlPatterns()).containsExactly("/*");
    }

    @Test
    @DisplayName("It is active only where the response logger is")
    void activeOnlyWithTheResponseLogger() {
        final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(SseLoggingBypassConfig.class)
                .withBean(ResponseContentLogger.class, () -> responseLogger);

        runner.run(context -> assertThat(context).doesNotHaveBean("sseAwareLoggingFilter"));
        runner.withPropertyValues("logging.loggers.method.enabled=true")
                .run(context -> assertThat(context).hasBean("sseAwareLoggingFilter")
                        .hasBean("disableAutoResponseContentLogger"));
    }
}
