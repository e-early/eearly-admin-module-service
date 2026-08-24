package si.result.project.eearly.facade.biometric;

import lombok.RequiredArgsConstructor;
import si.result.project.eearly.dto.biometric.BiometricRecordBatchDTO;
import si.result.project.eearly.dto.biometric.BiometricRecordTimeSeriesDTO;
import si.result.project.eearly.mapper.biometric.BiometricTimeSeriesMapper;
import si.result.project.eearly.model.biometric.command.BiometricFilterCommand;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.port.biometric.BiometricRecordService;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.project.eearly.port.patient.PatientService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("ClassCanBeRecord")
@Facade
@RequiredArgsConstructor
public class BiometricRecordServiceFacade {

  private final BiometricRecordService biometricRecordService;
  private final MeasurementService measurementService;
  private final BiometricTimeSeriesMapper biometricTimeSeriesMapper;
  private final PatientService patientService;

  public final BiometricRecordTimeSeriesDTO getBiometricRecordTimeSeries(final UUID patientId,
                                                                         final String measurementName,
                                                                         final String startDateTime,
                                                                         final String endDateTime,
                                                                         final BiometricDataAggregationLevel aggregationLevel) {
    
    final var patient = patientService.findById(patientId);
    final UUID ehrId = patient.getKeycloakId() != null ? patient.getKeycloakId() : patientId;

    final var measurement = measurementService.findByName(measurementName);
    final var measurementType = BiometricMeasurementType.fromString(measurementName);

    final var command = BiometricFilterCommand.builderFor(measurementType.valueClass())
            .ehrId(ehrId)
            .measurementName(measurementName)
            .measurementType(measurementType)
            .ehrObservationId(measurement.getEhrObservationId())
            .chartType(measurement.getChartType())
            .startDateTime(startDateTime)
            .endDateTime(endDateTime)
            .aggregationLevel(aggregationLevel)
            .build();

    return getBiometricRecordTimeSeriesGenericContextWrapper(command);
  }

  public BiometricRecordBatchDTO getBiometricRecordTimeSeriesBatch(
          final UUID patientId,
          final List<String> measurementNames,
          final String startDateTime,
          final String endDateTime,
          final BiometricDataAggregationLevel aggregationLevel
  ) {
    final var patient = patientService.findById(patientId);
    final UUID ehrId = patient.getKeycloakId() != null ? patient.getKeycloakId() : patientId;
    final Map<String, BiometricRecordTimeSeriesDTO> measurementMap = new LinkedHashMap<>();
    for (String measurementName : measurementNames) {
      final var measurement = measurementService.findByName(measurementName);
      final var measurementType = BiometricMeasurementType.fromString(measurementName);

      final var command = BiometricFilterCommand.builderFor(measurementType.valueClass())
              .ehrId(ehrId)
              .measurementName(measurementName)
              .measurementType(measurementType)
              .ehrObservationId(measurement.getEhrObservationId())
              .chartType(measurement.getChartType())
              .startDateTime(startDateTime)
              .endDateTime(endDateTime)
              .aggregationLevel(aggregationLevel)
              .build();

      measurementMap.put(measurementName, getBiometricRecordTimeSeriesGenericContextWrapper(command));
    }

    return BiometricRecordBatchDTO.builder()
            .patientId(patientId)
            .measurements(measurementMap)
            .build();
  }

  private <T extends MeasurementValue> BiometricRecordTimeSeriesDTO getBiometricRecordTimeSeriesGenericContextWrapper(
          final BiometricFilterCommand<T> command
  ) {
    final var records = biometricRecordService.findAll(command).toList();
    final var aggregatedRecords = biometricRecordService.getAggregatedRecords(records, command.aggregationLevel(), command.valueClass()).toList();
    final var statistics = biometricRecordService.getStatistics(records, command.valueClass());
    final var mostCommonUnit = biometricRecordService.getMostCommonUnit(records);
    final var firstRecordTime = biometricRecordService.getFirstRecordTimestamp(records);
    final var lastRecordTime = biometricRecordService.getLastRecordTimestamp(records);
    return biometricTimeSeriesMapper.toDTO(
            command.ehrId(),
            command.measurementName(),
            command.measurementType(),
            command.ehrObservationId(),
            command.aggregationLevel(),
            command.chartType(),
            mostCommonUnit,
            aggregatedRecords.size(),
            firstRecordTime,
            lastRecordTime,
            aggregatedRecords,
            statistics);
  }
}
