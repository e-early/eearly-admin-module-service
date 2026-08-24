package si.result.project.eearly.mapper.biometric;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.biometric.BiometricStatisticsDTO;
import si.result.project.eearly.dto.biometric.BloodPressureBiometricStatisticsDTO;
import si.result.project.eearly.model.biometric.BasicBiometricStatistics;
import si.result.project.eearly.model.biometric.BiometricStatistics;
import si.result.project.eearly.model.biometric.BloodPressureBiometricStatistics;

@Mapper
public interface BiometricStatisticsMapper {

  /**
   * Polymorphic mapping method to handle different BiometricStatistics types.
   *
   * @param statistics the BiometricStatistics instance (can be Basic or BloodPressure)
   * @return the corresponding BiometricStatisticsDTO
   */
  default BiometricStatisticsDTO toDTO(final BiometricStatistics<?> statistics) {
    return switch (statistics) {
      case null -> null;
      case BasicBiometricStatistics basic -> toBasicDTO(basic);
      case BloodPressureBiometricStatistics bloodPressure -> toBloodPressureDTO(bloodPressure);
      default -> throw new IllegalStateException("Unexpected value: " + statistics.getNonNullValue());
    };
  }

  // Concrete mappings for Basic (single value)
  @Mapping(target = "average", source = "statistics.average.value")
  @Mapping(target = "maximum", source = "statistics.maximum.value")
  @Mapping(target = "minimum", source = "statistics.minimum.value")
  @Mapping(target = "median", source = "statistics.median.value")
  @Mapping(target = "standardDeviation", source = "statistics.standardDeviation.value")
  @Mapping(target = "mostRecent", source = "statistics.mostRecent.value")
  @Mapping(target = "status", source = "statistics.status")
  BiometricStatisticsDTO toBasicDTO(final BasicBiometricStatistics statistics);

  // Concrete mappings for Blood Pressure (high/low)
  @Mapping(target = "average", source = "statistics.average.highValue")
  @Mapping(target = "maximum", source = "statistics.maximum.highValue")
  @Mapping(target = "minimum", source = "statistics.minimum.highValue")
  @Mapping(target = "median", source = "statistics.median.highValue")
  @Mapping(target = "standardDeviation", source = "statistics.standardDeviation.highValue")
  @Mapping(target = "mostRecent", source = "statistics.mostRecent.highValue")
  @Mapping(target = "status", source = "statistics.status")
  @Mapping(target = "highAverage", source = "statistics.average.highValue")
  @Mapping(target = "lowAverage", source = "statistics.average.lowValue")
  @Mapping(target = "highMaximum", source = "statistics.maximum.highValue")
  @Mapping(target = "lowMaximum", source = "statistics.maximum.lowValue")
  @Mapping(target = "highMinimum", source = "statistics.minimum.highValue")
  @Mapping(target = "lowMinimum", source = "statistics.minimum.lowValue")
  @Mapping(target = "highMedian", source = "statistics.median.highValue")
  @Mapping(target = "lowMedian", source = "statistics.median.lowValue")
  @Mapping(target = "highStandardDeviation", source = "statistics.standardDeviation.highValue")
  @Mapping(target = "lowStandardDeviation", source = "statistics.standardDeviation.lowValue")
  @Mapping(target = "highMostRecent", source = "statistics.mostRecent.highValue")
  @Mapping(target = "lowMostRecent", source = "statistics.mostRecent.lowValue")
  BloodPressureBiometricStatisticsDTO toBloodPressureDTO(final BloodPressureBiometricStatistics statistics);
}
