package si.result.project.eearly.dto.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.dto.department.DepartmentLiteDTO;

import java.util.List;
import java.util.UUID;

public record OrganizationDTO(

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,

        @Schema(example = "Test Organization")
        String name,

        List<DepartmentLiteDTO> departments

) {}
