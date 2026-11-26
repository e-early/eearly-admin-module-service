package si.result.project.eearly.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.dto.organization.OrganizationLiteDTO;

import java.util.UUID;

public record DepartmentDTO(

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,

        @Schema(example = "Test Department")
        String name,

        OrganizationLiteDTO organization

) {}
