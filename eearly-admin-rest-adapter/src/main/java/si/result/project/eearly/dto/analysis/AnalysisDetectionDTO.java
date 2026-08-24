package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.analysis.FeedbackState;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema
public record AnalysisDetectionDTO(

        @Schema(description = "Stable detection id; assigned server-side if omitted in algorithm callback")
        UUID id,

        @Schema(example = "2026-02-12T11:42:00+01:00")
        OffsetDateTime startTimestamp,

        @Schema(example = "2026-02-12T12:15:00+01:00")
        OffsetDateTime endTimestamp,

        @Schema(example = "0.92")
        Double probability,

        @Schema(description = "Caretaker feedback for this algorithm detection box; null when not set")
        FeedbackState caretakerFeedback,

        @Schema(description = "MODEL_PREDICTION for model detections; CARETAKER for chart-drawn regions")
        String source
) {}
