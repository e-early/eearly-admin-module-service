package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Data transfer object representing statistical data for blood pressure measurements.
 * It extends the {@link BiometricStatisticsDTO} to include both systolic (high) and diastolic (low) values.
 * To keep the API clean, the inherited fields from {@link BiometricStatisticsDTO} represent the systolic (high) values,
 * while both low and high values are also represented explicitly.
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = false)
public class BloodPressureBiometricStatisticsDTO extends BiometricStatisticsDTO {
  @Schema(example = "121")
  Double highAverage;

  @Schema(example = "62.5")
  Double lowAverage;

  @Schema(example = "140")
  Double highMaximum;

  @Schema(example = "66")
  Double lowMaximum;

  @Schema(example = "100")
  Double highMinimum;

  @Schema(example = "60")
  Double lowMinimum;

  @Schema(example = "122")
  Double highMedian;

  @Schema(example = "62")
  Double lowMedian;

  @Schema(example = "10.3")
  Double highStandardDeviation;

  @Schema(example = "12.3")
  Double lowStandardDeviation;

  @Schema(example = "120")
  Double highMostRecent;

  @Schema(example = "67")
  Double lowMostRecent;
}
