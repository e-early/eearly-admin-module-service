package si.result.project.eearly.dao.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.port.notification.NotificationRepository;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public interface NotificationDAO extends NotificationRepository,
        JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    @Query("""
            SELECT COUNT(n)
            FROM Notification n
            LEFT JOIN NotificationRead nr
              ON n.id = nr.id.notificationId AND nr.id.caretakerId = :caretakerId
            WHERE (n.caretakerId = :caretakerId OR n.caretakerId IS NULL)
              AND nr.id IS NULL
            """)
    long countUnreadNotificationsForCaretaker(UUID caretakerId);

    @Query("""
            SELECT n
            FROM Notification n
            LEFT JOIN NotificationRead nr
              ON n.id = nr.id.notificationId AND nr.id.caretakerId = :caretakerId
            WHERE (n.caretakerId = :caretakerId OR n.caretakerId IS NULL)
              AND nr.id IS NULL
            ORDER BY n.createdAt DESC
            """)
    List<Notification> getUnreadNotificationsForCaretaker(UUID caretakerId);
}
