package si.result.project.eearly.model.analysis;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DetectionBoxData(
        UUID id,
        OffsetDateTime startTimestamp,
        OffsetDateTime endTimestamp,
        Double probability,
        FeedbackState caretakerFeedback,
        String source
) {}
