package si.result.project.eearly.util;

import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.measurement.MeasurementType;

import java.util.List;

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
            case WEIGHT -> BiometricMeasurementType.BODY_WEIGHT;
            case HEIGHT -> BiometricMeasurementType.BODY_HEIGHT;
            case BLOOD_PRESSURE, BLOOD_PRESSURE_DIASTOLIC, BLOOD_PRESSURE_SYSTOLIC ->
                    BiometricMeasurementType.BLOOD_PRESSURE;
            case BLOOD_GLUCOSE -> BiometricMeasurementType.BLOOD_GLUCOSE;
            case HEART_RATE_VARIABILITY -> BiometricMeasurementType.HEART_RATE_VARIABILITY;
            case STRESS_LEVEL -> BiometricMeasurementType.STRESS_LEVEL;
            case GLUCOSE_VARIABILITY -> BiometricMeasurementType.GLUCOSE_VARIABILITY;
            default -> BiometricMeasurementType.fromString(measurementType.name());
        };
    }

    /**
     * The inverse of {@link #fromMeasurementType(MeasurementType)}: which {@link MeasurementType} catalog
     * entries a {@link BiometricMeasurementType} represents. Returns more than one entry only for
     * {@code BLOOD_PRESSURE}, which - unlike every other value - collapses the two sub-measurements
     * ({@code BLOOD_PRESSURE_SYSTOLIC}/{@code BLOOD_PRESSURE_DIASTOLIC}) rather than the generic
     * {@code BLOOD_PRESSURE} type, since only the sub-measurements have queryable openEHR observations.
     */
    public static List<MeasurementType> toMeasurementTypes(final BiometricMeasurementType biometricMeasurementType) {
        if (biometricMeasurementType == null) {
            return List.of();
        }

        return switch (biometricMeasurementType) {
            case BLOOD_PRESSURE -> List.of(MeasurementType.BLOOD_PRESSURE_SYSTOLIC, MeasurementType.BLOOD_PRESSURE_DIASTOLIC);
            case BLOOD_GLUCOSE -> List.of(MeasurementType.BLOOD_GLUCOSE);
            case HEART_RATE -> List.of(MeasurementType.HEART_RATE);
            case BODY_TEMPERATURE -> List.of(MeasurementType.BODY_TEMPERATURE);
            case BODY_WEIGHT -> List.of(MeasurementType.WEIGHT);
            case BODY_HEIGHT -> List.of(MeasurementType.HEIGHT);
            case BLOOD_OXYGEN -> List.of(MeasurementType.OXYGEN_SATURATION);
            case HEART_RATE_VARIABILITY -> List.of(MeasurementType.HEART_RATE_VARIABILITY);
            case STRESS_LEVEL -> List.of(MeasurementType.STRESS_LEVEL);
            case GLUCOSE_VARIABILITY -> List.of(MeasurementType.GLUCOSE_VARIABILITY);
            case BODY_MASS_INDEX -> List.of(MeasurementType.BODY_MASS_INDEX);
            case FAT_FREE_MASS -> List.of(MeasurementType.FAT_FREE_MASS);
            case FAT_MASS -> List.of(MeasurementType.FAT_MASS);
            case BODY_FAT_PERCENTAGE -> List.of(MeasurementType.BODY_FAT_PERCENTAGE);
            case BODY_WATER_PERCENTAGE -> List.of(MeasurementType.BODY_WATER_PERCENTAGE);
            case SKELETAL_MUSCLE_MASS -> List.of(MeasurementType.SKELETAL_MUSCLE_MASS);
            case BASAL_METABOLIC_RATE -> List.of(MeasurementType.BASAL_METABOLIC_RATE);
            case FAT_FREE_MASS_PERCENTAGE -> List.of(MeasurementType.FAT_FREE_MASS_PERCENTAGE);
            case SKELETAL_MUSCLE_MASS_PERCENTAGE -> List.of(MeasurementType.SKELETAL_MUSCLE_MASS_PERCENTAGE);
            case BODY_WATER_MASS -> List.of(MeasurementType.BODY_WATER_MASS);
            case UNKNOWN -> List.of();
        };
    }
}
