package si.result.project.eearly.model.biometric;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import si.result.project.eearly.model.biometric.measurement.BasicValue;

@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class BasicBiometricRecord extends BiometricRecord<BasicValue> {

}
