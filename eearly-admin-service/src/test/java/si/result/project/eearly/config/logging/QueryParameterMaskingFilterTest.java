package si.result.project.eearly.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class QueryParameterMaskingFilterTest {

    @Test
    @DisplayName("Sensitive values are replaced, all the others stay")
    void masksOnlySensitiveValues() {
        assertThat(QueryParameterMaskingFilter.mask("token=abc.def&page=2")).isEqualTo("token=***&page=2");
        assertThat(QueryParameterMaskingFilter.mask("page=2&token=abc")).isEqualTo("page=2&token=***");
        assertThat(QueryParameterMaskingFilter.mask("a=1&b=2")).isEqualTo("a=1&b=2");
    }

    @Test
    void masksEveryOccurrence_caseInsensitive_andEncodedNames() {
        assertThat(QueryParameterMaskingFilter.mask("token=a&TOKEN=b&Token=c")).isEqualTo("token=***&TOKEN=***&Token=***");
        assertThat(QueryParameterMaskingFilter.mask("%74oken=abc")).isEqualTo("%74oken=***");
        assertThat(QueryParameterMaskingFilter.mask("access_token=a&api_key=b&password=c&client_secret=d&secret=e"))
                .isEqualTo("access_token=***&api_key=***&password=***&client_secret=***&secret=***");
    }

    @Test
    void keepsEmptyAndValuelessPieces() {
        assertThat(QueryParameterMaskingFilter.mask(null)).isNull();
        assertThat(QueryParameterMaskingFilter.mask("")).isEmpty();
        assertThat(QueryParameterMaskingFilter.mask("token=")).isEqualTo("token=***");
        assertThat(QueryParameterMaskingFilter.mask("token&a=1")).isEqualTo("token&a=1");
        assertThat(QueryParameterMaskingFilter.mask("a=1&&b=2")).isEqualTo("a=1&&b=2");
        assertThat(QueryParameterMaskingFilter.mask("notatoken=abc&tokens=abc")).isEqualTo("notatoken=abc&tokens=abc");
    }

    @Test
    @DisplayName("Downstream sees a masked query string, yet the real value through the parameter API")
    void wrapsTheRequest() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/analyses/1/result");
        request.setQueryString("token=secret-value&x=1");
        request.setParameter("token", "secret-value");
        request.setParameter("x", "1");
        final AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        final FilterChain chain = (req, res) -> seen.set((HttpServletRequest) req);

        new QueryParameterMaskingFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen.get().getQueryString()).isEqualTo("token=***&x=1").doesNotContain("secret-value");
        assertThat(seen.get().getParameter("token")).isEqualTo("secret-value");
        assertThat(seen.get().getParameterValues("token")).containsExactly("secret-value");
        assertThat(seen.get().getRequestURI()).isEqualTo("/api/v1/analyses/1/result");
    }

    @Test
    void requestWithoutQueryIsPassedOn() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        final AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

        new QueryParameterMaskingFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set((HttpServletRequest) req));

        assertThat(seen.get().getQueryString()).isNull();
    }

    @Test
    void isRegisteredFirst() {
        final var registration = new RequestLogMaskingConfig().queryParameterMaskingFilter();

        assertThat(registration.getOrder()).isLessThan(-105); // before the request context and security filters
        assertThat(registration.getUrlPatterns()).containsExactly("/*");
    }
}
