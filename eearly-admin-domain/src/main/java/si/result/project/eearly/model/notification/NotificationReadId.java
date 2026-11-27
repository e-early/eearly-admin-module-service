package si.result.project.eearly.model.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.validation.Condition;

import java.util.UUID;

@Getter
@Embeddable
@EqualsAndHashCode
public class NotificationReadId {

    @Column(name = "id_caretaker")
    private UUID caretakerId;

    @Column(name = "id_notification")
    private UUID notificationId;

    public static NotificationReadId create(final UUID caretakerId, final UUID notificationId) {
        final NotificationReadId id = new NotificationReadId();
        id.setCaretakerId(caretakerId);
        id.setNotificationId(notificationId);
        return id;
    }

    private void setCaretakerId(final UUID caretakerId) {
        Condition.checkNotNull(caretakerId, DomainExceptionCode.CARETAKER_ID_NULL);
        this.caretakerId = caretakerId;
    }

    private void setNotificationId(final UUID notificationId) {
        Condition.checkNotNull(notificationId, DomainExceptionCode.NOTIFICATION_ID_NULL);
        this.notificationId = notificationId;
    }
}
