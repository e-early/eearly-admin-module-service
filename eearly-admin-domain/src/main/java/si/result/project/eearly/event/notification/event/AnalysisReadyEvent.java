package si.result.project.eearly.event.notification.event;

import java.util.UUID;

public record AnalysisReadyEvent(UUID analysisId, UUID patientId) {
}
