package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema
@Data
@EqualsAndHashCode(callSuper = true)
public class BasicBiometricDataPointDTO extends BiometricDataPointDTO {
}