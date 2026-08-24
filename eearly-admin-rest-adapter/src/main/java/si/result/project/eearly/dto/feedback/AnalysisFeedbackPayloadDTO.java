package si.result.project.eearly.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema
public record AnalysisFeedbackPayloadDTO(

        @Schema(description = "Analysis identifier")
        UUID analysisId,

        @Schema(description = "Patient identifier")
        UUID patientId,

        @Schema(description = "Caretaker identifier assigned to the patient")
        UUID caretakersId,

        @Schema(description = "Diagnosis type derived from algorithm category", example = "RESPIRATORY")
        String diagnosisType,

        @Schema(description = "Data source type", example = "AdminApp")
        String datasourceType,

        @Schema(description = "Algorithm / model name")
        String modelName,

        @Schema(description = "Algorithm version")
        String modelVersion,

        @Schema(description = "Model classification threshold (0–1) for this model name/version", example = "0.5")
        Double modelThreshold,

        @Schema(description = "Detection boxes for this analysis")
        List<DetectionBoxFeedbackDTO> detectionBoxes
) {}
