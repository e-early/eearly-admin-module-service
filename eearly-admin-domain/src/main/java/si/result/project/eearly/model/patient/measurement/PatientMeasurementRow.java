package si.result.project.eearly.model.patient.measurement;

import si.result.project.eearly.model.measurement.MeasurementType;

import java.time.Instant;
import java.util.UUID;

public record PatientMeasurementRow(
        UUID batchId,
        MeasurementType measurementType,
        String unit,
        Instant timestamp,
        double value
) {
}
