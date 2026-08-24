package si.result.project.eearly.mapper.biometric;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.biometric.BiometricRecordTimeSeriesDTO;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BiometricStatistics;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.measurement.MeasurementChartType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Mapper(uses = {BiometricStatisticsMapper.class, BiometricDataPointMapper.class})
public interface BiometricTimeSeriesMapper {

  @SuppressWarnings({"squid:S00107", "mapper is allowed to have many parameters"})
  @Mapping(target = "dataPoints", source = "records")
  BiometricRecordTimeSeriesDTO toDTO(final UUID id,
                                     final String measurementName,
                                     final BiometricMeasurementType measurementType,
                                     final String ehrObservationId,
                                     final BiometricDataAggregationLevel aggregationLevel,
                                     final MeasurementChartType chartType,
                                     final String unit,
                                     final Integer totalRecords,
                                     final ZonedDateTime firstRecordTime,
                                     final ZonedDateTime lastRecordTime,
                                     final List<? extends BiometricRecord<?>> records,
                                     final BiometricStatistics<?> statistics);
}
