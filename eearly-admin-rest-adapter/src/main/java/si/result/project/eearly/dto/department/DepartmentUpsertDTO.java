package si.result.project.eearly.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema
public record DepartmentUpsertDTO(

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,

        @Schema(example = "Test Department")
        String name,

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID organizationId

) {}
