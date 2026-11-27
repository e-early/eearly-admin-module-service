package si.result.project.eearly.port.notification;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationRead;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.model.notification.command.CreateNotificationReadCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

/**
 * Notifications of a caretaker. A caretaker sees the notifications addressed to them and those
 * addressed to everyone (no caretaker), and the read state is kept per caretaker.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String CARETAKER_ID = "caretakerId";

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;

    public Page<Notification> findAll(final Specification<Notification> specification, final Pageable pageable,
            final UUID caretakerId) {
        return notificationRepository.findAll(forCaretaker(caretakerId).and(specification), pageable);
    }

    public Page<Notification> findUnread(final Specification<Notification> specification, final Pageable pageable,
            final UUID caretakerId) {
        return notificationRepository.findAll(unreadFor(caretakerId).and(specification), pageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(final CreateNotificationCommand command) {
        return notificationRepository.save(Notification.create(command));
    }

    public long getUnreadNotificationCountForCaretaker(final UUID caretakerId) {
        return notificationRepository.countUnreadNotificationsForCaretaker(caretakerId);
    }

    /**
     * Marks the notification as read for the caretaker of the command. A notification that is
     * addressed to another caretaker is reported as not found.
     */
    public void markNotificationAsRead(final CreateNotificationReadCommand command) {
        final Notification notification = notificationRepository.findById(command.notificationId())
                .filter(found -> found.isVisibleTo(command.caretakerId()))
                .orElseThrow(() -> new DomainException(DomainExceptionCode.NOTIFICATION_NOT_FOUND));
        notificationReadRepository.save(NotificationRead.create(
                new CreateNotificationReadCommand(command.caretakerId(), notification.getId())));
    }

    public void markAllNotificationsAsRead(final UUID caretakerId) {
        final List<Notification> unread = notificationRepository.getUnreadNotificationsForCaretaker(caretakerId);
        unread.forEach(notification -> notificationReadRepository.save(NotificationRead.create(
                new CreateNotificationReadCommand(caretakerId, notification.getId()))));
    }

    private Specification<Notification> forCaretaker(final UUID caretakerId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get(CARETAKER_ID), caretakerId),
                cb.isNull(root.get(CARETAKER_ID)));
    }

    /**
     * Visible to the caretaker and not read by that caretaker. What other caretakers have read does not matter.
     */
    private Specification<Notification> unreadFor(final UUID caretakerId) {
        return (root, query, cb) -> {
            final Subquery<Long> readByCaretaker = query.subquery(Long.class);
            final Root<NotificationRead> read = readByCaretaker.from(NotificationRead.class);
            readByCaretaker.select(cb.literal(1L)).where(
                    cb.equal(read.get("id").get("notificationId"), root.get("id")),
                    cb.equal(read.get("id").get("caretakerId"), caretakerId));
            return cb.and(
                    cb.or(cb.equal(root.get(CARETAKER_ID), caretakerId), cb.isNull(root.get(CARETAKER_ID))),
                    cb.not(cb.exists(readByCaretaker)));
        };
    }
}
