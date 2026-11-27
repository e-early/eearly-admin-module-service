package si.result.project.eearly.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import si.result.project.eearly.mapper.notification.NotificationMapperImpl;
import si.result.project.eearly.port.notification.NoOpSseNotificationPublisher;
import si.result.project.eearly.port.notification.NotificationPublisherAutoConfiguration;
import si.result.project.eearly.port.notification.SseNotificationPublisher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry replaces the no-op default publisher: the application is wired as in the service (component scan
 * of the adapter package, then the auto-configurations), and exactly one publisher bean exists, the real one.
 */
@DisplayName("SseNotificationPublisher replacement")
class SseNotificationPublisherReplacementTest {

    @Configuration
    @ComponentScan(basePackageClasses = SseEmitterRegistry.class)
    static class AdapterScan {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AdapterScan.class)
            .withBean(NotificationMapperImpl.class)
            .withConfiguration(AutoConfigurations.of(NotificationPublisherAutoConfiguration.class));

    @Test
    @DisplayName("Exactly one publisher bean exists and it is the registry, not the no-op default")
    void registryReplacesTheNoOpPublisher() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(SseNotificationPublisher.class)).hasSize(1);
            assertThat(context).hasSingleBean(SseNotificationPublisher.class);
            assertThat(context.getBean(SseNotificationPublisher.class))
                    .isInstanceOf(SseEmitterRegistry.class)
                    .isSameAs(context.getBean(SseEmitterRegistry.class))
                    .isNotInstanceOf(NoOpSseNotificationPublisher.class);
            assertThat(context).doesNotHaveBean("noOpSseNotificationPublisher");
        });
    }

    @Test
    @DisplayName("Without the adapter the no-op default stays in place")
    void withoutTheAdapter_noOpDefaultIsUsed() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NotificationPublisherAutoConfiguration.class))
                .run(context -> assertThat(context.getBean(SseNotificationPublisher.class))
                        .isInstanceOf(NoOpSseNotificationPublisher.class));
    }
}
