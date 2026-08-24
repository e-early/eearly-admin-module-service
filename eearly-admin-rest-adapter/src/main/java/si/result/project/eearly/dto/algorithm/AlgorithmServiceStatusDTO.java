package si.result.project.eearly.dto.algorithm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record AlgorithmServiceStatusDTO(
        @Schema(example = "true")
        boolean healthy,

        @Schema(example = "1.0.0")
        String version,

        @Schema(example = "ok")
        String message
) {}
