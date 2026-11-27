package si.result.project.eearly.port.notification;

import si.result.project.eearly.model.notification.Notification;

/**
 * Pushes a created notification to connected clients. Core only declares the port and supplies a
 * no-op default; a transport implementation replaces it.
 */
public interface SseNotificationPublisher {

    void broadcast(Notification notification);
}
