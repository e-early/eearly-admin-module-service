package si.result.project.eearly.mapper.biometric;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.biometric.BasicBiometricDataPointDTO;
import si.result.project.eearly.dto.biometric.BiometricDataPointDTO;
import si.result.project.eearly.dto.biometric.BloodPressureDataPointDTO;
import si.result.project.eearly.model.biometric.BasicBiometricRecord;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BloodPressureBiometricRecord;

import java.util.stream.Stream;

@Mapper
public interface BiometricDataPointMapper {

  default Stream<BiometricDataPointDTO> toDTO(final Stream<? extends BiometricRecord<?>> biometricRecords) {
    return biometricRecords.map(this::toDTO);
  }

  default BiometricDataPointDTO toDTO(final BiometricRecord<?> biometricRecord) {
    return switch (biometricRecord) {
      case BasicBiometricRecord basic -> toDTO(basic);
      case BloodPressureBiometricRecord bloodPressure -> toDTO(bloodPressure);
      default -> throw new IllegalArgumentException("Unsupported biometric record type: " + biometricRecord.getClass().getName());
    };
  }

  @Mapping(target = "value", source = "biometricRecord.value.value")
  BasicBiometricDataPointDTO toDTO(final BasicBiometricRecord biometricRecord);

  @Mapping(target = "value", source = "biometricRecord.value.highValue")
  @Mapping(target = "highValue", source = "biometricRecord.value.highValue")
  @Mapping(target = "lowValue", source = "biometricRecord.value.lowValue")
  BloodPressureDataPointDTO toDTO(final BloodPressureBiometricRecord biometricRecord);
}
