package si.result.project.eearly.model.notification;

import com.google.common.base.Objects;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import si.result.project.eearly.model.notification.command.CreateNotificationReadCommand;

/**
 * The fact that one caretaker has read one notification. The read state is per caretaker.
 */
@Entity
@Table(name = "t_notification_read")
@Getter
public class NotificationRead {

    @EmbeddedId
    private NotificationReadId id;

    @ManyToOne
    @JoinColumn(name = "id_notification", insertable = false, updatable = false)
    private Notification notification;

    protected NotificationRead() {
    }

    public static NotificationRead create(final CreateNotificationReadCommand command) {
        final NotificationRead notificationRead = new NotificationRead();
        notificationRead.setId(NotificationReadId.create(command.caretakerId(), command.notificationId()));
        return notificationRead;
    }

    private void setId(final NotificationReadId id) {
        this.id = id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationRead entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
