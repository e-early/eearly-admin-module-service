package si.result.project.eearly.model.biometric.measurement;

/**
 * Blood pressure value consisting of systolic (upper) and diastolic (lower).
 * <p>
 * According to medical convention (AHA, WebMD), systolic is always reported first,
 * then diastolic (e.g. 120/80). This {@code compareTo} follows that convention:
 * it compares systolic first, then diastolic if systolic values are equal.
 */
public record BloodPressureValue(
        Double lowValue,
        Double highValue
) implements MeasurementValue {

  public int compareTo(final MeasurementValue other) {
    if (!(other instanceof BloodPressureValue)) {
      throw new IllegalArgumentException("Cannot compare BloodPressureValue with " + other.getClass().getSimpleName());
    }
    int highValueCompare = Double.compare(this.highValue, ((BloodPressureValue) other).highValue);
    if (highValueCompare != 0) {
      return highValueCompare;
    }
    return Double.compare(this.lowValue, ((BloodPressureValue) other).lowValue);
  }
}