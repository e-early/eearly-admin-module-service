package si.result.project.eearly.port.biometric;

import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementRow;

import java.util.List;
import java.util.UUID;

public interface BiometricMeasurementSource {

    List<PatientMeasurementRow> getMeasurements(
            UUID patientKeycloakId,
            List<BiometricMeasurementType> measurementTypes,
            String startDate,
            String endDate
    );
}
