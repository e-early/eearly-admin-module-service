package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema
@Data
@EqualsAndHashCode(callSuper = true)
public class BloodPressureDataPointDTO extends BiometricDataPointDTO {

  @Schema(example = "80")
  private Double lowValue;

  @Schema(example = "120")
  private Double highValue;

}