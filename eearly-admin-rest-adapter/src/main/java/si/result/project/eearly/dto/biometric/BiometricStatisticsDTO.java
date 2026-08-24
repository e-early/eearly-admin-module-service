package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema
@Data
@EqualsAndHashCode
public class BiometricStatisticsDTO {
  @Schema(example = "84.5")
  Double average;

  @Schema(example = "100")
  Double maximum;

  @Schema(example = "60")
  Double minimum;

  @Schema(example = "82")
  Double median;

  @Schema(example = "12.3")
  Double standardDeviation;

  @Schema(example = "22.3")
  Double mostRecent;

  @Schema(example = "NORMAL", allowableValues = "NORMAL, HIGH, LOW, CRITICAL")
  String status;
}
