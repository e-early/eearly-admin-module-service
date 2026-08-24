package si.result.project.eearly.model.biometric.enumeration;

import lombok.RequiredArgsConstructor;
import si.result.project.eearly.model.biometric.measurement.BasicValue;
import si.result.project.eearly.model.biometric.measurement.BloodPressureValue;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;

@RequiredArgsConstructor
public enum BiometricMeasurementType {
  BLOOD_PRESSURE(BloodPressureValue.class),
  BLOOD_GLUCOSE(BasicValue.class),
  HEART_RATE(BasicValue.class),
  BODY_TEMPERATURE(BasicValue.class),
  BODY_WEIGHT(BasicValue.class),
  BODY_HEIGHT(BasicValue.class),
  BLOOD_OXYGEN(BasicValue.class),
  UNKNOWN(BasicValue.class);

  private final Class<? extends MeasurementValue> valueClass;

  public Class<? extends MeasurementValue> valueClass() {
    return valueClass;
  }

  public static BiometricMeasurementType fromString(String type) {
    try {
      return BiometricMeasurementType.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
