package si.result.project.eearly.dto.measurement;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.measurement.MeasurementChartType;
import si.result.project.eearly.model.measurement.MeasurementType;

import java.util.UUID;

@Schema
public record MeasurementDTO (
    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Blood pressure")
    String name,

    @Schema(example = "openEHR-EHR-OBSERVATION.blood_pressure.v2")
    String ehrObservationId,

    @Schema(example = "BLOOD_PRESSURE")
    MeasurementType type,

    @Schema(example = "LINE")
    MeasurementChartType chartType
)
{}
