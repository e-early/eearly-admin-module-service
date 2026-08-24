package si.result.project.eearly.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.dto.algorithm.AlgorithmLiteDTO;
import si.result.project.eearly.dto.measurement.MeasurementLiteDTO;
import si.result.project.eearly.model.gender.GenderType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema
public record PatientDTO(
    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Luka")
    String firstName,

    @Schema(example = "Rus")
    String lastName,

    @Schema(example = "2023-03-24")
    LocalDate dateOfBirth,

    @Schema(example = "FEMALE")
    GenderType gender,

    @Schema(example = "1234567890")
    String healthInsuranceId,

    @Schema(example = "Celovška 111")
    String street,

    @Schema(example = "Ljubljana")
    String city,

    @Schema(example = "Primorska")
    String state,

    @Schema(example = "1234")
    Integer zip,

    @Schema(example = "Slovenija")
    String country,

    @Schema(example = "luka.senko@result.si")
    String email,

    @Schema(example = "1234567890")
    String phoneNumber,

    List<MeasurementLiteDTO> measurements,

    List<AlgorithmLiteDTO> algorithms
) {}
