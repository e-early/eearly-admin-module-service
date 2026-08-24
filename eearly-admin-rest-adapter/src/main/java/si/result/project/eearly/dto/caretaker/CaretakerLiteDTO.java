package si.result.project.eearly.dto.caretaker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema
public record CaretakerLiteDTO (
        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID keycloakId,

        @Schema(example = "Luka")
        String firstName,

        @Schema(example = "Kuka")
        String lastName,

        @Schema(example = "luka@resut.si")
        String email,

        @Schema(example = "sl")
        String language
){}
