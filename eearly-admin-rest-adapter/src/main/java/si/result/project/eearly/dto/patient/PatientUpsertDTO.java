package si.result.project.eearly.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.gender.GenderType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema
public record PatientUpsertDTO(
        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,

        @Schema(example = "Luka")
        String firstName,

        @Schema(example = "Rus")
        String lastName,

        @Schema(example = "2023-02-23")
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

        List<UUID> caretakerList,

        List<UUID> measurementList,

        List<UUID> algorithmsList
) {}
