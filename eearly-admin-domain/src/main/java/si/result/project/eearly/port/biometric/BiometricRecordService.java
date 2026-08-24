package si.result.project.eearly.port.biometric;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BiometricStatistics;
import si.result.project.eearly.model.biometric.command.BiometricFilterCommand;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.model.measurement.MeasurementType;
import si.result.project.eearly.port.biometric.mapper.BiometricMeasurementRowMapper;
import si.result.project.eearly.port.biometric.processor.factory.BiometricRecordProcessorFactory;
import si.result.project.eearly.util.BiometricMeasurementTypeMapper;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BiometricRecordService {

  private final BiometricMeasurementSource biometricMeasurementSource;
  private final BiometricMeasurementRowMapper biometricMeasurementRowMapper;

  public <T extends MeasurementValue> Stream<BiometricRecord<T>> findAll(final BiometricFilterCommand<T> command) {
    final var patientRows = biometricMeasurementSource.getMeasurements(
            command.ehrId(),
            List.of(command.measurementType()),
            command.startDateTime(),
            command.endDateTime()
    );

    final var filteredEhrRecords = patientRows.stream()
            .filter(row -> matchesCommandType(row.measurementType(), command.measurementType()))
            .map(biometricMeasurementRowMapper::toEhrRecord)
            .toList();

    final var processorFactory = new BiometricRecordProcessorFactory();
    final Class<T> clazz = command.valueClass();
    final var processor = processorFactory.getProcessor(clazz);

    return processor.processRecords(filteredEhrRecords);
  }

  private boolean matchesCommandType(
          final MeasurementType measurementType,
          final BiometricMeasurementType commandMeasurementType
  ) {
    if (commandMeasurementType == BiometricMeasurementType.BLOOD_PRESSURE) {
      return measurementType == MeasurementType.BLOOD_PRESSURE
              || measurementType == MeasurementType.BLOOD_PRESSURE_SYSTOLIC
              || measurementType == MeasurementType.BLOOD_PRESSURE_DIASTOLIC;
    }
    return BiometricMeasurementTypeMapper.fromMeasurementType(measurementType) == commandMeasurementType;
  }

  public <T extends MeasurementValue> Stream<BiometricRecord<T>> getAggregatedRecords(List<BiometricRecord<T>> records, final BiometricDataAggregationLevel aggregationLevel, final Class<T> clazz) {
    final var processorFactory = new BiometricRecordProcessorFactory();
    final var processor = processorFactory.getProcessor(clazz);
    return processor.aggregateRecords(records, aggregationLevel);
  }

  public <T extends MeasurementValue> BiometricStatistics<T> getStatistics(List<BiometricRecord<T>> records, final Class<T> clazz) {
    final var processorFactory = new BiometricRecordProcessorFactory();
    final var processor = processorFactory.getProcessor(clazz);

    return processor.calculateStatistics(records.stream());
  }

  public <T extends MeasurementValue> String getMostCommonUnit(final List<BiometricRecord<T>> records) {
    return records.stream().collect(Collectors.groupingBy(BiometricRecord::getUnit, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
  }

  public <T extends MeasurementValue> ZonedDateTime getFirstRecordTimestamp(final List<BiometricRecord<T>> records) {
    return records.stream().min(BiometricRecord::compareTimestampTo)
            .map(BiometricRecord::getTimestamp)
            .orElse(null);
  }

  public <T extends MeasurementValue> ZonedDateTime getLastRecordTimestamp(final List<BiometricRecord<T>> records) {
    return records.stream().max(BiometricRecord::compareTimestampTo)
            .map(BiometricRecord::getTimestamp)
            .orElse(null);
  }
}
