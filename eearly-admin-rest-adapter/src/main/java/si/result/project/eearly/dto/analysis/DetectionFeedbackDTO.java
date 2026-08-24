package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.analysis.FeedbackState;

@Schema
public record DetectionFeedbackDTO(

        @Schema(example = "CONFIRMED")
        FeedbackState feedback

) {
}
