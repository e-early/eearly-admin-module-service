package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.analysis.AnalysisState;

import java.util.List;
import java.util.UUID;

@Schema
public record AnalysisResultCallbackDTO(

        @Schema(example = "a1b2c3d4-5678-9abc-def0-1234567890ab")
        UUID correlationId,

        @Schema(example = "e4f5a6b7-1234-5678-9abc-def012345678")
        UUID analysisId,

        @Schema(example = "WAITING_FOR_CONFIRMATION")
        AnalysisState state,

        @Schema(example = "true")
        Boolean detected,

        @Schema(example = "Apnea detection")
        String name,

        @Schema(description = "Detected episodes")
        List<AnalysisDetectionDTO> detections
) {
}
