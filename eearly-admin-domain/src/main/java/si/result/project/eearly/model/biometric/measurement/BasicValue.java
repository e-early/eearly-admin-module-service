package si.result.project.eearly.model.biometric.measurement;

/**
 * A basic biometric value represented by a single Double.
 */
public record BasicValue(
        Double value) implements MeasurementValue {

  public int compareTo(final MeasurementValue other) {
    if (!(other instanceof BasicValue)) {
      throw new IllegalArgumentException("Cannot compare BasicValue with " + other.getClass().getSimpleName());
    }
    return Double.compare(this.value, ((BasicValue) other).value);
  }
}