package si.result.project.eearly.port.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.notification.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository {

    Page<Notification> findAll(Specification<Notification> specification, Pageable pageable);

    Notification save(Notification notification);

    long countUnreadNotificationsForCaretaker(UUID caretakerId);

    List<Notification> getUnreadNotificationsForCaretaker(UUID caretakerId);

    Optional<Notification> findById(UUID id);
}
