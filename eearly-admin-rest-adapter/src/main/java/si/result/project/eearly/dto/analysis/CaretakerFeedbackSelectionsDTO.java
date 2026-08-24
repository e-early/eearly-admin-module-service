package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Caretaker-drawn chart intervals; persisted as analysis detection boxes")
public record CaretakerFeedbackSelectionsDTO(

        @Schema(description = "Replaces existing caretaker feedback regions; same shape as create-analysis selections")
        List<AnalysisSelectionDTO> selections
) {}
