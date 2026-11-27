package si.result.project.eearly.model.notification.command;

import si.result.project.eearly.model.notification.NotificationType;

import java.util.UUID;

public record CreateNotificationCommand(
        NotificationType type,
        String message,
        UUID patientId,
        UUID caretakerId,
        UUID referenceId
) {
}
