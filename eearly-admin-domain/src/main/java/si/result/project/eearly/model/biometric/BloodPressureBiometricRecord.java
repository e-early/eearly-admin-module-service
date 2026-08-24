package si.result.project.eearly.model.biometric;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import si.result.project.eearly.model.biometric.measurement.BloodPressureValue;

@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class BloodPressureBiometricRecord extends BiometricRecord<BloodPressureValue> {
}
