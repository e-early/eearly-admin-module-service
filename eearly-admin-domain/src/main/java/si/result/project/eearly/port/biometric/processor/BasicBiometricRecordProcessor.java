package si.result.project.eearly.port.biometric.processor;

import si.result.project.eearly.model.biometric.BasicBiometricRecord;
import si.result.project.eearly.model.biometric.BasicBiometricStatistics;
import si.result.project.eearly.model.biometric.BiometricRecord;
import si.result.project.eearly.model.biometric.BiometricStatistics;
import si.result.project.eearly.model.biometric.enumeration.BiometricAnalysisCriticalityType;
import si.result.project.eearly.model.biometric.measurement.BasicValue;
import si.result.project.eearly.model.ehr.EhrRecord;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

public class BasicBiometricRecordProcessor extends BiometricRecordProcessor<BasicValue> {

  @Override
  public Stream<BiometricRecord<BasicValue>> processRecords(Stream<EhrRecord> records) {
    return records.map(this::processRecord);
  }

  @Override
  protected BiometricStatistics<BasicValue> createStatistics(BasicValue minimum, BasicValue maximum, BasicValue median, BasicValue average, BasicValue standardDeviation, BasicValue mostRecent, BiometricAnalysisCriticalityType status) {
    return BasicBiometricStatistics.builder()
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
  protected BiometricRecord<BasicValue> createBiometricRecord(final ZonedDateTime timestamp,
                                                              final String batchId,
                                                              final BiometricAnalysisCriticalityType status,
                                                              final String unit,
                                                              final BasicValue value,
                                                              final String aggregationKey,
                                                              final Integer aggregationCount) {
    return BasicBiometricRecord.builder()
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
  protected BasicValue getAverage(List<BiometricRecord<BasicValue>> sortedRecords) {
    return new BasicValue(sortedRecords.stream()
            .mapToDouble(a -> a.getValue().value())
            .average()
            .orElseThrow(() -> new IllegalStateException("This method should not be called on empty list")));
  }

  @Override
  protected BasicValue getStandardDeviation(List<BiometricRecord<BasicValue>> sortedRecords, BasicValue average) {
    final var deviationValue = sortedRecords.stream()
            .mapToDouble(a -> a.getValue().value())
            .map(value -> Math.pow(value - average.value(), 2))
            .sum() / sortedRecords.size();

    return new BasicValue(Math.sqrt(deviationValue));
  }


  private BiometricRecord<BasicValue> processRecord(final EhrRecord ehrRecord) {
    return BasicBiometricRecord.builder()
            .timestamp(ehrRecord.measurementTime())
            .batchId(ehrRecord.batchId())
            .status(BiometricAnalysisCriticalityType.UNKNOWN)
            .unit(ehrRecord.unit())
            .value(new BasicValue(ehrRecord.magnitude()))
            .build();
  }
}
