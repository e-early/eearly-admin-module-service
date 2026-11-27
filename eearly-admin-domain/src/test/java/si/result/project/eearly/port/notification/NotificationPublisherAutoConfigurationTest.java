package si.result.project.eearly.port.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import si.result.project.eearly.model.notification.Notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class NotificationPublisherAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotificationPublisherAutoConfiguration.class));

    @Test
    void providesTheNoOpPublisherByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SseNotificationPublisher.class);
            assertThat(context.getBean(SseNotificationPublisher.class))
                    .isInstanceOf(NoOpSseNotificationPublisher.class);
            assertThatCode(() -> context.getBean(SseNotificationPublisher.class)
                    .broadcast(mock(Notification.class))).doesNotThrowAnyException();
        });
    }

    @Test
    void backsOffWhenTheApplicationProvidesAPublisher() {
        runner.withBean("customPublisher", SseNotificationPublisher.class, () -> notification -> { })
                .run(context -> {
                    assertThat(context).hasSingleBean(SseNotificationPublisher.class);
                    assertThat(context).hasBean("customPublisher");
                    assertThat(context.getBean(SseNotificationPublisher.class))
                            .isNotInstanceOf(NoOpSseNotificationPublisher.class);
                });
    }
}
