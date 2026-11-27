package si.result.project.eearly.config.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link QueryParameterMaskingFilter} ahead of every other servlet filter, request context and security
 * included, so that no later component, in particular the request logging, sees a credential in the query string.
 */
@Configuration
public class RequestLogMaskingConfig {

    @Bean
    public FilterRegistrationBean<QueryParameterMaskingFilter> queryParameterMaskingFilter() {
        final FilterRegistrationBean<QueryParameterMaskingFilter> registration =
                new FilterRegistrationBean<>(new QueryParameterMaskingFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
