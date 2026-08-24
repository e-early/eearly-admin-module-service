package si.result.project.eearly.port.biometric.processor;

import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import si.result.project.eearly.model.biometric.*;
import si.result.project.eearly.model.ehr.EhrRecord;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;
import si.result.project.eearly.model.biometric.measurement.BloodPressureValue;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class BloodPressureBiometricRecordProcessor extends BiometricRecordProcessor<BloodPressureValue> {

  private static final String DIASTOLIC_TYPE = "diastolic";
  private static final String SYSTOLIC_TYPE = "systolic";

  @SuppressWarnings({"LoggingSimilarMessage"}) // Because the messages are similar but contextually different
  @Override
  public Stream<BiometricRecord<BloodPressureValue>> processRecords(final Stream<EhrRecord> records) {
    final var byBloodPressureTypeMap = records.collect(Collectors.groupingBy(EhrRecord::getLowercasedMeasurementType));

    if (!validateMeasurementTypes(byBloodPressureTypeMap)) {
      return Stream.empty();
    }

    final var byBatchIdSystolicRecords = byBloodPressureTypeMap.get(SYSTOLIC_TYPE).stream()
            .collect(Collectors.toMap(EhrRecord::batchId, Function.identity(), (first, second) -> {
              log.warn("Duplicate systolic record for time {}: choosing one with batchId {}. The following will be discarded (batchId): {}", first.measurementTime(), first.batchId(), second.batchId());
              return first;
            }));
    final var byBatchIdDiastolicRecords = byBloodPressureTypeMap.get(DIASTOLIC_TYPE).stream()
            .collect(Collectors.toMap(EhrRecord::batchId, Function.identity(), (first, second) -> {
              log.warn("Duplicate systolic record for time {}: choosing one with batchId {}. The following will be discarded (batchId): {}", first.measurementTime(), first.batchId(), second.batchId());
              return first;
            }));

    final var commonBatchIds = new HashSet<>(byBatchIdDiastolicRecords.keySet());
    commonBatchIds.retainAll(byBatchIdSystolicRecords.keySet());

    validateBatchIds(commonBatchIds, byBatchIdDiastolicRecords, byBatchIdSystolicRecords);

    return commonBatchIds.stream()
            .map(batchId -> {
              final var systolicRecord = byBatchIdSystolicRecords.get(batchId);
              final var diastolicRecord = byBatchIdDiastolicRecords.get(batchId);

              return BloodPressureBiometricRecord.builder()
                      .timestamp(systolicRecord.measurementTime())
                      .batchId(batchId)
                      .status(BiometricAnalysisCriticalityType.UNKNOWN)
                      .unit(systolicRecord.unit())
                      .value(new BloodPressureValue(diastolicRecord.magnitude(), systolicRecord.magnitude()))
                      .build();
            });
  }

  @Override
  protected BiometricStatistics<BloodPressureValue> createStatistics(BloodPressureValue minimum, BloodPressureValue maximum, BloodPressureValue median, BloodPressureValue average, BloodPressureValue standardDeviation, BloodPressureValue mostRecent, BiometricAnalysisCriticalityType status) {
    return BloodPressureBiometricStatistics.builder()
            .minimum(minimum)
            .maximum(maximum)
            .median(median)
            .average(average)
            .standardDeviation(standardDeviation)
            .mostRecent(mostRecent)
            .status(status)
            .build();
  }

  @Override
  protected BiometricRecord<BloodPressureValue> createBiometricRecord(final ZonedDateTime timestamp,
                                                                      final String batchId,
                                                                      final BiometricAnalysisCriticalityType status,
                                                                      final String unit,
                                                                      final BloodPressureValue value,
                                                                      final String aggregationKey,
                                                                      final Integer aggregationCount) {
    return BloodPressureBiometricRecord.builder()
            .timestamp(timestamp)
            .batchId(batchId)
            .status(status)
            .unit(unit)
            .value(value)
            .aggregationKey(aggregationKey)
            .aggregationCount(aggregationCount)
            .build();
  }

  @Override
  protected BloodPressureValue getAverage(@NotEmpty final List<BiometricRecord<BloodPressureValue>> sortedRecords) {
    final var low = sortedRecords.stream()
            .mapToDouble(biometricRecord -> biometricRecord.getValue().lowValue())
            .average()
            .orElseThrow(() -> new IllegalStateException("This method should not be called on empty list"));

    final var high = sortedRecords.stream()
            .mapToDouble(biometricRecord -> biometricRecord.getValue().highValue())
            .average()
            .orElseThrow(() -> new IllegalStateException("This method should not be called on empty list"));

    return new BloodPressureValue(low, high);
  }

  @Override
  protected BloodPressureValue getStandardDeviation(@NotEmpty final List<BiometricRecord<BloodPressureValue>> sortedRecords,
                                                    final BloodPressureValue average) {
    final var meanLow = average.lowValue();
    final var meanHigh = average.highValue();

    final var low = sortedRecords.stream()
            .mapToDouble(biometricRecord -> biometricRecord.getValue().lowValue())
            .map(lowValue -> Math.pow(lowValue - meanLow, 2))
            .sum() / sortedRecords.size();

    final var high = sortedRecords.stream()
            .mapToDouble(biometricRecord -> biometricRecord.getValue().highValue())
            .map(highValue -> Math.pow(highValue - meanHigh, 2))
            .sum() / sortedRecords.size();

    return new BloodPressureValue(Math.sqrt(low), Math.sqrt(high));
  }


  private boolean validateMeasurementTypes(final Map<String, List<EhrRecord>> byBloodPressureType) {

    if (!byBloodPressureType.containsKey(SYSTOLIC_TYPE) || !byBloodPressureType.containsKey(DIASTOLIC_TYPE)) {
      log.warn("Missing required blood pressure measurement types: systolic and/or diastolic. Found types: {}",
          byBloodPressureType.keySet());
      return false;
    }

    if (byBloodPressureType.size() != 2) {
      log.warn("Expected exactly 2 blood pressure measurement types (systolic and diastolic), but found: {}",
              byBloodPressureType.keySet());
    }

    if (byBloodPressureType.get(DIASTOLIC_TYPE).size() != byBloodPressureType.get(SYSTOLIC_TYPE).size()) {
      log.warn("Mismatched number of systolic and diastolic records: systolic count = {}, diastolic count = {}",
              byBloodPressureType.get(SYSTOLIC_TYPE).size(), byBloodPressureType.get(DIASTOLIC_TYPE).size());
    }

    return true;
  }

  private void validateBatchIds(final Set<String> batchIds,
                                final Map<String, EhrRecord> byBatchIdDiastolicRecords,
                                final Map<String, EhrRecord> byBatchIdSystolicRecords) {
    if (batchIds.size() != byBatchIdDiastolicRecords.size() || batchIds.size() != byBatchIdSystolicRecords.size()) {
      log.warn("Some diastolic records do not have matching systolic records. Common batch IDs: {}, Diastolic batch IDs: {}, Systolic batch IDs {}",
              batchIds.size(),
              byBatchIdDiastolicRecords.size(),
              byBatchIdSystolicRecords.size());
    }
  }
}
