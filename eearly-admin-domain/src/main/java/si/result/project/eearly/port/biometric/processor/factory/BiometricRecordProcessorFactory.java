package si.result.project.eearly.port.biometric.processor.factory;

import si.result.project.eearly.model.biometric.measurement.BasicValue;
import si.result.project.eearly.model.biometric.measurement.BloodPressureValue;
import si.result.project.eearly.model.biometric.measurement.MeasurementValue;
import si.result.project.eearly.port.biometric.processor.BasicBiometricRecordProcessor;
import si.result.project.eearly.port.biometric.processor.BiometricRecordProcessor;
import si.result.project.eearly.port.biometric.processor.BloodPressureBiometricRecordProcessor;

import java.util.HashMap;
import java.util.Map;

public class BiometricRecordProcessorFactory {
  private final Map<Class<?>, BiometricRecordProcessor<?>> processors = new HashMap<>();
  private final BasicBiometricRecordProcessor defaultProcessor = new BasicBiometricRecordProcessor();

  public BiometricRecordProcessorFactory() {
    registerProcessor(BloodPressureValue.class, new BloodPressureBiometricRecordProcessor());
    registerProcessor(BasicValue.class, defaultProcessor);
  }

  private <T extends MeasurementValue> void registerProcessor(final Class<?> clazz,
                                                              final BiometricRecordProcessor<T> processor) {
    processors.putIfAbsent(clazz, processor);
  }

  @SuppressWarnings("unchecked") // Safe cast based on registration logic
  public <T extends MeasurementValue> BiometricRecordProcessor<T> getProcessor(final Class<T> clazz) {
    return (BiometricRecordProcessor<T>) processors.getOrDefault(clazz, defaultProcessor);
  }
}
