package si.result.project.eearly.port.biometric.processor;

import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.model.ehr.EhrRecord;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BiometricStatistics;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BiometricRecordProcessor<T extends MeasurementValue> {
  public abstract Stream<BiometricRecord<T>> processRecords(final Stream<EhrRecord> records);

  public Stream<BiometricRecord<T>> processRecords(final List<EhrRecord> records) {
    return processRecords(records.stream());
  }

  protected abstract BiometricStatistics<T> createStatistics(final T minimum,
                                                             final T maximum,
                                                             final T median,
                                                             final T average,
                                                             final T standardDeviation,
                                                             final T mostRecent,
                                                             final BiometricAnalysisCriticalityType status);

  public BiometricStatistics<T> calculateStatistics(final Stream<BiometricRecord<T>> records) {
    final var sortedList = records.sorted(BiometricRecord::compareValueTo).toList();

    if (sortedList.isEmpty()) {
      return createStatistics(null,
              null,
              null,
              null,
              null,
              null,
              null);
    }
    final var minimum = getMinimum(sortedList);
    final var maximum = getMaximum(sortedList);
    final var median = getMedian(sortedList);
    final var average = getAverage(sortedList);
    final var standardDeviation = getStandardDeviation(sortedList, average);
    final var mostRecent = getMostRecent(sortedList);
    return createStatistics(minimum,
            maximum,
            median,
            average,
            standardDeviation,
            mostRecent,
            BiometricAnalysisCriticalityType.UNKNOWN);
  }

  public Stream<BiometricRecord<T>> aggregateRecords(final List<BiometricRecord<T>> records, final BiometricDataAggregationLevel aggregationLevel) {
    if (aggregationLevel == BiometricDataAggregationLevel.NONE) {
      return records.stream().sorted(BiometricRecord::compareTimestampTo);
    }

    final var aggregatedMap = records.stream().collect(Collectors.groupingBy(biometricRecord -> getAggregationRecordKey(biometricRecord, aggregationLevel)));
    final var aggregatedList = aggregatedMap.entrySet().stream()
            .map(entry -> {
              final var aggregationKey = entry.getKey();
              final var entryRecords = entry.getValue();
              final var aggregatedValue = aggregateValues(entryRecords);
              final var aggregationCount = entryRecords.size();
              final var representativeRecord = entryRecords.getFirst();
              return createBiometricRecord(representativeRecord.getTimestamp(),
                      representativeRecord.getBatchId(),
                      representativeRecord.getStatus(),
                      representativeRecord.getUnit(),
                      aggregatedValue,
                      aggregationKey,
                      aggregationCount);
            })
            .sorted(BiometricRecord::compareTimestampTo)
            .toList();
    return aggregatedList.stream();
  }

  protected abstract BiometricRecord<T> createBiometricRecord(final ZonedDateTime timestamp,
                                                              final String batchId,
                                                              final BiometricAnalysisCriticalityType status,
                                                              final String unit,
                                                              final T value,
                                                              final String aggregationKey,
                                                              final Integer aggregationCount);

  protected T getMinimum(final List<BiometricRecord<T>> sortedRecords) {
    return sortedRecords.getFirst().getValue();
  }

  protected T getMaximum(final List<BiometricRecord<T>> sortedRecords) {
    return sortedRecords.getLast().getValue();
  }

  protected T getMedian(final List<BiometricRecord<T>> sortedRecords) {
    return sortedRecords.get(sortedRecords.size() / 2).getValue();
  }

  protected abstract T getAverage(final List<BiometricRecord<T>> sortedRecords);

  protected abstract T getStandardDeviation(final List<BiometricRecord<T>> sortedRecords, final T average);

  protected T getMostRecent(final List<BiometricRecord<T>> sortedRecords) {
    return sortedRecords.stream()
            .max(BiometricRecord::compareTimestampTo)
            .map(BiometricRecord::getValue)
            .orElseThrow(() -> new IllegalStateException("This method should not be called on empty list"));
  }

  protected String getAggregationRecordKey(final BiometricRecord<T> biometricRecord, final BiometricDataAggregationLevel aggregationLevel) {
    ZonedDateTime timestamp = biometricRecord.getTimestamp();
    return switch (aggregationLevel) {
      case SECOND -> timestamp.truncatedTo(ChronoUnit.SECONDS).toString();
      case MINUTE -> timestamp.truncatedTo(ChronoUnit.MINUTES).toString();
      case HOUR -> timestamp.truncatedTo(ChronoUnit.HOURS).toString();
      case DAY -> timestamp.toLocalDate().toString();
      case WEEK -> timestamp.get(IsoFields.WEEK_BASED_YEAR) + "-W" + timestamp.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
      default -> timestamp.toString();
    };
  }

  protected T aggregateValues(final List<BiometricRecord<T>> values) {
    return this.getAverage(values.stream().sorted(BiometricRecord::compareTimestampTo).toList());
  }
}
