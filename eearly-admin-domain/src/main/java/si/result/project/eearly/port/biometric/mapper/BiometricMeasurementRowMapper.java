package si.result.project.eearly.port.biometric.mapper;

import org.springframework.stereotype.Component;
import si.result.project.eearly.model.ehr.EhrRecord;
import si.result.project.eearly.model.measurement.MeasurementType;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementRow;

import java.time.ZoneOffset;

@Component
public class BiometricMeasurementRowMapper {

    public EhrRecord toEhrRecord(PatientMeasurementRow row) {
        return new EhrRecord(
                null,
                toProcessorMeasurementType(row.measurementType()),
                row.unit(),
                row.value(),
                row.batchId() == null ? null : row.batchId().toString(),
                row.timestamp().atZone(ZoneOffset.UTC)
        );
    }

    private String toProcessorMeasurementType(MeasurementType type) {
        if (type == null) {
            return "unknown";
        }

        return switch (type) {
            case BLOOD_PRESSURE_SYSTOLIC -> "systolic";
            case BLOOD_PRESSURE_DIASTOLIC -> "diastolic";
            case BLOOD_PRESSURE -> "blood_pressure";
            default -> type.name().toLowerCase();
        };
    }
}
