package si.result.project.eearly.port.notification;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the no-op {@link SseNotificationPublisher} unless the application provides one.
 * An auto-configuration, so that the condition is evaluated after all application beans are known.
 */
@AutoConfiguration
public class NotificationPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SseNotificationPublisher.class)
    public SseNotificationPublisher noOpSseNotificationPublisher() {
        return new NoOpSseNotificationPublisher();
    }
}
