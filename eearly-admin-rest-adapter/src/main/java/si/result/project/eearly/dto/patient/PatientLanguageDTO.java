package si.result.project.eearly.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record PatientLanguageDTO(
        @Schema(example = "en", allowableValues = {"sl", "en"})
        String language
) {}
