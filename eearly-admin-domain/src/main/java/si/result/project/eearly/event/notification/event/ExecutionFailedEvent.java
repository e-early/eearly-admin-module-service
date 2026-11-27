package si.result.project.eearly.event.notification.event;

import java.util.UUID;

public record ExecutionFailedEvent(UUID executionId, UUID patientId, String algorithmName) {
}
