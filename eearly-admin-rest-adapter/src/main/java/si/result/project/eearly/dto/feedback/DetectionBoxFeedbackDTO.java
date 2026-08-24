package si.result.project.eearly.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import si.result.project.eearly.model.analysis.DetectionBoxAuditStatus;
import si.result.project.eearly.model.analysis.FeedbackState;

@Schema
public record DetectionBoxFeedbackDTO(

        @Schema(description = "Detection box identifier")
        UUID detectionBoxId,

        @Schema(description = "Current interval start (may differ after caretaker resize)")
        OffsetDateTime startTimestamp,

        @Schema(description = "Current interval end (may differ after caretaker resize)")
        OffsetDateTime endTimestamp,

        @Schema(description = "Interval start when the box was first created")
        OffsetDateTime originalStartTimestamp,

        @Schema(description = "Interval end when the box was first created")
        OffsetDateTime originalEndTimestamp,

        @Schema(example = "0.152467", description = "Model probability; unchanged by caretaker feedback")
        Double probability,

        @Schema(description = "Caretaker feedback; null when not set")
        FeedbackState caretakerFeedback,

        @Schema(description = "MODEL_PREDICTION or CARETAKER", example = "MODEL_PREDICTION")
        String source,

        @Schema(description = "When the detection box was first created")
        OffsetDateTime statusCreatedTimestamp,

        @Schema(description = "When the detection box was last updated")
        OffsetDateTime statusLastUpdateTimestamp,

        @Schema(description = "CREATED, EDITED, or DELETED")
        DetectionBoxAuditStatus status
) {}
