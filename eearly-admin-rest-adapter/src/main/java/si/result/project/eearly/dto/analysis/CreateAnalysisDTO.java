package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema
public record CreateAnalysisDTO(

        @Schema(example = "Apnea detection")
        String name,

        @Schema(example = "d3c2b1a0-9876-5432-1fed-cba987654321")
        UUID patientId,

        @Schema(example = "d3c2b1a0-9876-5432-1fed-cba987654321")
        UUID algorithmId,

        @Schema(description = "Selected measurement intervals to analyze")
        List<AnalysisSelectionDTO> selections,

        @Schema(description = "Chart display parameters")
        ChartDisplayOptionsDTO chartDisplayOptions

) {}
