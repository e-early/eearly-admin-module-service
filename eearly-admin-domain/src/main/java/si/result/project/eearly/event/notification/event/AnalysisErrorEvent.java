package si.result.project.eearly.event.notification.event;

import java.util.UUID;

public record AnalysisErrorEvent(UUID analysisId, UUID patientId) {
}
