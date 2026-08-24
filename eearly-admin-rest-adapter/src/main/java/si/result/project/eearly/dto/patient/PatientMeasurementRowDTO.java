package si.result.project.eearly.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.measurement.MeasurementType;

import java.time.Instant;
import java.util.UUID;

@Schema
public record PatientMeasurementRowDTO(
        @Schema(example = "3d51f149-4eb2-42af-b217-8f5a754968eb")
        UUID batchId,

        @Schema(example = "HEART_RATE")
        MeasurementType measurementType,

        @Schema(example = "BPM")
        String unit,

        @Schema(example = "2026-05-04T12:55:15.918Z")
        Instant timestamp,

        @Schema(example = "66.8414709848079")
        double value
) {
}
