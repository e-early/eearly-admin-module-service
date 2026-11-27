package si.result.project.eearly.port.notification;

import lombok.extern.slf4j.Slf4j;
import si.result.project.eearly.model.notification.Notification;

/**
 * Default publisher used when no transport is configured: it pushes nothing. It logs only the
 * notification id, never its content.
 */
@Slf4j
public class NoOpSseNotificationPublisher implements SseNotificationPublisher {

    @Override
    public void broadcast(final Notification notification) {
        log.debug("No notification transport configured, notification {} not pushed", notification.getId());
    }
}
