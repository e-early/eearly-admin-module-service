package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;

import java.time.ZonedDateTime;

@Schema
@Data
public abstract class BiometricDataPointDTO {
  @Schema(example = "2025-08-06T22:03:59Z")
  private ZonedDateTime timestamp;

  @Schema(example = "6c35b14f-1206-4357-8a13-a0c7428a80d6::local.ehrbase.org::1")
  private String batchId;

  @Schema(example = "NORMAL")
  private BiometricAnalysisCriticalityType status;

  @Schema(example = "82")
  private Double value;

  @Schema(example = "2025-W2",
          examples = "2025-08-06T22:03:59Z, 2025-08-06T22:00:00Z, 2025-08-06, 2025-W32",
          description = "aggregation key representing the time period this data point belongs to, e.g. exact time, hour, day, week")
  private String aggregationKey;

  @Schema(example = "3", description = "number of original data points that were aggregated into this data point")
  private Integer aggregationCount;

}