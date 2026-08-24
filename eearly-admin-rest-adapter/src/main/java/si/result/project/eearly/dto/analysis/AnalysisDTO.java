package si.result.project.eearly.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.dto.measurement.MeasurementLiteDTO;
import si.result.project.eearly.dto.patient.PatientLiteDTO;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.FeedbackState;

import java.util.List;
import java.util.UUID;

@Schema
public record AnalysisDTO(
    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Apnea detection")
    String name,

    @Schema(example = "ORDERED")
    AnalysisState state,

    @Schema(example = "true")
    Boolean detected,

    @Schema(example = "NONE")
    FeedbackState feedback,

    @Schema()
    PatientLiteDTO patient,

    @Schema()
    List<MeasurementLiteDTO> measurements,

    @Schema(description = "Input selection intervals used for this analysis")
    List<AnalysisSelectionDTO> inputParameters,

    @Schema(description = "Algorithm detection boxes loaded from persistent storage")
    List<AnalysisDetectionDTO> detections,

    @JsonProperty("caretaker_feedback_detections")
    @Schema(description = "Caretaker-drawn detection boxes loaded from persistent storage")
    List<AnalysisDetectionDTO> caretakerFeedbackDetections,

    @Schema(description = "Chart display parameters")
    ChartDisplayOptionsDTO chartDisplayOptions

) {}
