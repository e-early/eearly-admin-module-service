package si.result.project.eearly.model.biometric;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ObjectUtils;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
@SuperBuilder
public abstract class BiometricRecord<T extends MeasurementValue> {
  private final ZonedDateTime timestamp;
  private final String batchId;
  private final BiometricAnalysisCriticalityType status;
  private final String unit;
  private final T value;
  private final String aggregationKey;
  private final Integer aggregationCount;

  public int compareValueTo(final BiometricRecord<T> other) {
    if (other == null) {
      return 1; // any non-null value is considered greater than null
    }
    return this.value.compareTo(other.value);
  }

  public int compareTimestampTo(final BiometricRecord<T> other) {
    if (other == null) {
      return 1; // any non-null value is considered greater than null
    }
    return ObjectUtils.compare(this.timestamp, other.timestamp);
  }
}
