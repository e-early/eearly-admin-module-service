package si.result.project.eearly.model.patient.measurement;

import java.util.List;

public record PatientMeasurementPage(
        List<PatientMeasurementRow> measurements,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
