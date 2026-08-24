package si.result.project.eearly.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema
public record AnalysisFeedbacksResponseDTO(

        @Schema(description = "Analysis payloads with detection boxes for ML sync")
        List<AnalysisFeedbackPayloadDTO> analysisPayload
) {}
