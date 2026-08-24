package si.result.project.eearly.dto.algorithm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Algorithm status change request")
public record AlgorithmStatusDTO(

    @Schema(example = "ACTIVE")
    String status

) {
}
