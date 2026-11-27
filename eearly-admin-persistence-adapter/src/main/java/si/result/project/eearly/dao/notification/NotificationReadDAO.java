package si.result.project.eearly.dao.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import si.result.project.eearly.model.notification.NotificationRead;
import si.result.project.eearly.model.notification.NotificationReadId;
import si.result.project.eearly.port.notification.NotificationReadRepository;

@SuppressWarnings("unused")
public interface NotificationReadDAO extends NotificationReadRepository,
        JpaRepository<NotificationRead, NotificationReadId> {
}
