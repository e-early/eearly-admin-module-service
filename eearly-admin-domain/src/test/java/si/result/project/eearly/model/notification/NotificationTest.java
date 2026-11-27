package si.result.project.eearly.model.notification;

import org.junit.jupiter.api.Test;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static void assertRejected(final CreateNotificationCommand command, final DomainExceptionCode code) {
        assertThatThrownBy(() -> Notification.create(command))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void createKeepsTheCommandValues() {
        final UUID patientId = UUID.randomUUID();
        final UUID caretakerId = UUID.randomUUID();
        final UUID referenceId = UUID.randomUUID();

        final Notification notification = Notification.create(new CreateNotificationCommand(
                NotificationType.ALGORITHM_EXECUTION, "ALGORITHM_EXECUTION_STARTED:Test", patientId, caretakerId,
                referenceId));

        assertThat(notification.getType()).isEqualTo(NotificationType.ALGORITHM_EXECUTION);
        assertThat(notification.getMessage()).isEqualTo("ALGORITHM_EXECUTION_STARTED:Test");
        assertThat(notification.getPatientId()).isEqualTo(patientId);
        assertThat(notification.getCaretakerId()).isEqualTo(caretakerId);
        assertThat(notification.getReferenceId()).isEqualTo(referenceId);
    }

    @Test
    void typeAndMessageAreRequired() {
        assertRejected(new CreateNotificationCommand(null, "X", null, null, null),
                DomainExceptionCode.NOTIFICATION_TYPE_NULL);
        assertRejected(new CreateNotificationCommand(NotificationType.SYNC, null, null, null, null),
                DomainExceptionCode.NOTIFICATION_MESSAGE_NULL);
    }

    @Test
    void messageIsLimitedTo255Characters() {
        assertRejected(new CreateNotificationCommand(NotificationType.SYNC, "x".repeat(256), null, null, null),
                DomainExceptionCode.NOTIFICATION_MESSAGE_TOO_LONG);
        assertThat(Notification.create(
                new CreateNotificationCommand(NotificationType.SYNC, "x".repeat(255), null, null, null)).getMessage())
                .hasSize(255);
    }

    @Test
    void referenceIsRequiredForAnalysisExecutionAndThresholdNotificationsOnly() {
        for (final NotificationType type : new NotificationType[] {NotificationType.ANALYSIS,
                NotificationType.ALGORITHM_EXECUTION, NotificationType.THRESHOLD_ALERT}) {
            assertRejected(new CreateNotificationCommand(type, "X", null, null, null),
                    DomainExceptionCode.REFERENCE_ID_NULL);
        }
        for (final NotificationType type : new NotificationType[] {NotificationType.SYNC, NotificationType.ERROR,
                NotificationType.MEASUREMENT}) {
            assertThat(Notification.create(new CreateNotificationCommand(type, "X", null, null, null))
                    .getReferenceId()).isNull();
        }
    }

    @Test
    void notificationWithoutCaretakerIsVisibleToEveryone() {
        final UUID caretakerId = UUID.randomUUID();
        final Notification toEveryone = Notification.create(
                new CreateNotificationCommand(NotificationType.SYNC, "X", null, null, null));
        final Notification toOne = Notification.create(
                new CreateNotificationCommand(NotificationType.SYNC, "X", null, caretakerId, null));

        assertThat(toEveryone.isVisibleTo(UUID.randomUUID())).isTrue();
        assertThat(toOne.isVisibleTo(caretakerId)).isTrue();
        assertThat(toOne.isVisibleTo(UUID.randomUUID())).isFalse();
    }
}
