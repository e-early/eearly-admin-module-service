package si.result.project.eearly.port.notification;

import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.notification.NotificationRead;

@Repository
public interface NotificationReadRepository {

    NotificationRead save(NotificationRead notificationRead);
}
