package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.dto.patient.PatientLiteDTO;
import si.result.project.eearly.model.analysis.AnalysisState;

import java.util.UUID;

@Schema
public record AnalysisLiteDTO(
    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Apnea detection")
    String name,

    @Schema(example = "ORDERED")
    AnalysisState state,

    @Schema(example = "true")
    boolean detected,

    @Schema(example = "")
    PatientLiteDTO patient

) {}
