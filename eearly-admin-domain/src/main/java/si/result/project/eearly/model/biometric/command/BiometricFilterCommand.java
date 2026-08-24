package si.result.project.eearly.model.biometric.command;

import lombok.Builder;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.model.measurement.MeasurementChartType;

import java.util.UUID;

@Builder
public record BiometricFilterCommand<T extends MeasurementValue>(
        UUID ehrId,
        String measurementName,
        BiometricMeasurementType measurementType,
        Class<T> valueClass,
        String ehrObservationId,
        MeasurementChartType chartType,
        String startDateTime,
        String endDateTime,
        BiometricDataAggregationLevel aggregationLevel
) {

  public static <T extends MeasurementValue> BiometricFilterCommandBuilder<T> builderFor(final Class<T> valueClass) {
    return BiometricFilterCommand.<T>builder().valueClass(valueClass);
  }
}
