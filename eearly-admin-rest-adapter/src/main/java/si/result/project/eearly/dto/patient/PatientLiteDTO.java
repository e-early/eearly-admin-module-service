package si.result.project.eearly.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.gender.GenderType;
import si.result.project.eearly.model.measurement.MeasurementStatus;
import si.result.project.eearly.model.patient.PatientStatus;

import java.time.LocalDate;
import java.util.UUID;

@Schema
public record PatientLiteDTO(

    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Luka")
    String firstName,

    @Schema(example = "Rus")
    String lastName,

    @Schema(example = "FEMALE")
    GenderType gender,

    @Schema(example = "2000-01-01")
    LocalDate dateOfBirth,

    @Schema(example = "1234567890")
    String healthInsuranceId,

    @Schema(example = "HEALTHY")
    PatientStatus patientStatus,

    @Schema(example = "OK")
    MeasurementStatus measurementStatus
) {}
