package si.result.project.eearly.port.patient;

import org.springframework.data.domain.Pageable;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementPage;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementRow;

import java.util.List;
import java.util.UUID;

public interface PatientMeasurementClient {

    List<PatientMeasurementRow> getMeasurements(UUID patientKeycloakId, List<Measurement> measurements);

    PatientMeasurementPage getMeasurements(UUID patientKeycloakId, List<Measurement> measurements,
                                           Pageable pageable);

    PatientMeasurementPage getMeasurements(UUID patientKeycloakId, List<Measurement> measurements,
                                           Pageable pageable, String startDate, String endDate);
}
