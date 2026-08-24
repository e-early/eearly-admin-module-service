package si.result.project.eearly.model.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BiometricStatistics<T> {
  @Schema(example = "84.5")
  private T average;

  @Schema(example = "100")
  private T maximum;

  @Schema(example = "60")
  private T minimum;

  @Schema(example = "82")
  private T median;

  @Schema(example = "12.3")
  private T standardDeviation;

  @Schema(example = "13.3")
  private T mostRecent;

  @Schema(example = "NORMAL")
  private BiometricAnalysisCriticalityType status;

  public T getNonNullValue() {
    if (average != null) {
      return average;
    } else if (maximum != null) {
      return maximum;
    } else if (minimum != null) {
      return minimum;
    } else if (median != null) {
      return median;
    } else if (standardDeviation != null) {
      return standardDeviation;
    } else {
      return mostRecent;
    }
  }
}
