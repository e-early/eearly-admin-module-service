package si.result.project.eearly.dto.algorithm;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema
public record AlgorithmLiteDTO(
    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Apnea detection AI algorithm")
    String name,

    @Schema(example = "1.0.0")
    String algorithmVersion,

    @Schema(example = "CARDIOVASCULAR")
    String category,

    @Schema(example = "ACTIVE")
    String status,

    @Schema(example = "FREE")
    String pricingModel

){}
