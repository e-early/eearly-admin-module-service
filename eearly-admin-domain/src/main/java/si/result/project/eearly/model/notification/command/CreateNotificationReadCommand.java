package si.result.project.eearly.model.notification.command;

import java.util.UUID;

public record CreateNotificationReadCommand(
        UUID caretakerId,
        UUID notificationId
) {
}
