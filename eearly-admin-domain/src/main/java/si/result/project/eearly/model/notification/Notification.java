package si.result.project.eearly.model.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.spring.boot.bricks.validation.Condition;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An in-app notification for one caretaker, or for every caretaker when the caretaker is null.
 * It holds only ids, a type and a message code, no personal data.
 */
@Entity
@Table(name = "t_notification")
@EntityListeners(AuditingEntityListener.class)
@Getter
public class Notification {

    private static final int MAX_MESSAGE_LENGTH = 255;

    @Id
    @Column(name = "id")
    @UuidGenerator
    private UUID id;

    @Column(name = "id_patient")
    private UUID patientId;

    @Column(name = "id_caretaker")
    private UUID caretakerId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type")
    private NotificationType type;

    @Column(name = "message")
    private String message;

    @Column(name = "id_reference")
    private UUID referenceId;

    @CreatedDate
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "notification")
    private List<NotificationRead> notificationReads;

    protected Notification() {
    }

    public boolean isNotificationSeen(final UUID caretakerId) {
        return notificationReads.stream()
                .anyMatch(notificationRead -> notificationRead.getId().getCaretakerId().equals(caretakerId));
    }

    /**
     * Whether the caretaker may see the notification: it is addressed to the caretaker or to everyone.
     */
    public boolean isVisibleTo(final UUID caretakerId) {
        return this.caretakerId == null || this.caretakerId.equals(caretakerId);
    }

    public static Notification create(final CreateNotificationCommand command) {
        final Notification notification = new Notification();
        notification.setType(command.type());
        notification.setMessage(command.message());
        notification.setReferenceId(command.referenceId());
        notification.setPatientId(command.patientId());
        notification.setCaretakerId(command.caretakerId());
        return notification;
    }

    private void setType(final NotificationType type) {
        Condition.checkNotNull(type, DomainExceptionCode.NOTIFICATION_TYPE_NULL);
        this.type = type;
    }

    private void setMessage(final String message) {
        Condition.checkNotNull(message, DomainExceptionCode.NOTIFICATION_MESSAGE_NULL);
        Condition.check(message.length() <= MAX_MESSAGE_LENGTH, DomainExceptionCode.NOTIFICATION_MESSAGE_TOO_LONG);
        this.message = message;
    }

    private void setReferenceId(final UUID referenceId) {
        if (type == NotificationType.ANALYSIS
                || type == NotificationType.ALGORITHM_EXECUTION
                || type == NotificationType.THRESHOLD_ALERT) {
            Condition.checkNotNull(referenceId, DomainExceptionCode.REFERENCE_ID_NULL);
        }
        this.referenceId = referenceId;
    }

    private void setPatientId(final UUID patientId) {
        this.patientId = patientId;
    }

    private void setCaretakerId(final UUID caretakerId) {
        this.caretakerId = caretakerId;
    }
}
