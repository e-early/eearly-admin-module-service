package si.result.project.eearly.event.notification.event;

import java.util.UUID;

public record ExecutionStartedEvent(UUID executionId, UUID patientId, String algorithmName) {
}
