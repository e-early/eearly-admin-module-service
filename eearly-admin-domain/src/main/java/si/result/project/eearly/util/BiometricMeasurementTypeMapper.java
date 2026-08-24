package si.result.project.eearly.util;

import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.measurement.MeasurementType;

public final class BiometricMeasurementTypeMapper {

    private BiometricMeasurementTypeMapper() {
    }

    public static BiometricMeasurementType fromMeasurementType(final MeasurementType measurementType) {
        if (measurementType == null) {
            return BiometricMeasurementType.UNKNOWN;
        }

        return switch (measurementType) {
            case OXYGEN_SATURATION -> BiometricMeasurementType.BLOOD_OXYGEN;
            case BODY_TEMPERATURE -> BiometricMeasurementType.BODY_TEMPERATURE;
            case HEART_RATE -> BiometricMeasurementType.HEART_RATE;
            case BLOOD_PRESSURE, BLOOD_PRESSURE_DIASTOLIC, BLOOD_PRESSURE_SYSTOLIC ->
                    BiometricMeasurementType.BLOOD_PRESSURE;
            case BLOOD_GLUCOSE -> BiometricMeasurementType.BLOOD_GLUCOSE;
            default -> BiometricMeasurementType.fromString(measurementType.name());
        };
    }
}
