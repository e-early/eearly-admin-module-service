package si.result.project.eearly.util;

import org.junit.jupiter.api.Test;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.measurement.MeasurementType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BiometricMeasurementTypeMapperTest {

    @Test
    void fromMeasurementType_mapsNewVariabilityTypes() {
        assertEquals(BiometricMeasurementType.HEART_RATE_VARIABILITY,
                BiometricMeasurementTypeMapper.fromMeasurementType(MeasurementType.HEART_RATE_VARIABILITY));
        assertEquals(BiometricMeasurementType.STRESS_LEVEL,
                BiometricMeasurementTypeMapper.fromMeasurementType(MeasurementType.STRESS_LEVEL));
        assertEquals(BiometricMeasurementType.GLUCOSE_VARIABILITY,
                BiometricMeasurementTypeMapper.fromMeasurementType(MeasurementType.GLUCOSE_VARIABILITY));
    }

    @Test
    void fromString_resolvesNewVariabilityTypes() {
        assertEquals(BiometricMeasurementType.HEART_RATE_VARIABILITY,
                BiometricMeasurementType.fromString("HEART_RATE_VARIABILITY"));
        assertEquals(BiometricMeasurementType.STRESS_LEVEL,
                BiometricMeasurementType.fromString("STRESS_LEVEL"));
        assertEquals(BiometricMeasurementType.GLUCOSE_VARIABILITY,
                BiometricMeasurementType.fromString("GLUCOSE_VARIABILITY"));

        assertNotEquals(BiometricMeasurementType.UNKNOWN,
                BiometricMeasurementType.fromString("HEART_RATE_VARIABILITY"));
        assertNotEquals(BiometricMeasurementType.UNKNOWN,
                BiometricMeasurementType.fromString("STRESS_LEVEL"));
        assertNotEquals(BiometricMeasurementType.UNKNOWN,
                BiometricMeasurementType.fromString("GLUCOSE_VARIABILITY"));
    }

    @Test
    void toMeasurementTypes_forBloodPressure_returnsBothSubTypesNotTheGenericType() {
        assertThat(BiometricMeasurementTypeMapper.toMeasurementTypes(BiometricMeasurementType.BLOOD_PRESSURE))
                .containsExactlyInAnyOrder(MeasurementType.BLOOD_PRESSURE_SYSTOLIC, MeasurementType.BLOOD_PRESSURE_DIASTOLIC);
    }

    @Test
    void toMeasurementTypes_forUnknown_returnsEmptyList() {
        assertThat(BiometricMeasurementTypeMapper.toMeasurementTypes(BiometricMeasurementType.UNKNOWN)).isEmpty();
    }

    @Test
    void toMeasurementTypes_forNull_returnsEmptyList() {
        assertThat(BiometricMeasurementTypeMapper.toMeasurementTypes(null)).isEmpty();
    }

    @Test
    void toMeasurementTypes_isTheInverseOfFromMeasurementType_forEverySingleValuedType() {
        for (BiometricMeasurementType biometricType : BiometricMeasurementType.values()) {
            if (biometricType == BiometricMeasurementType.BLOOD_PRESSURE || biometricType == BiometricMeasurementType.UNKNOWN) {
                continue;
            }
            List<MeasurementType> measurementTypes = BiometricMeasurementTypeMapper.toMeasurementTypes(biometricType);
            assertThat(measurementTypes).hasSize(1);
            assertEquals(biometricType, BiometricMeasurementTypeMapper.fromMeasurementType(measurementTypes.get(0)));
        }
    }

    @Test
    void bodyCompositionTypesMapBothWays() {
        List<MeasurementType> bodyComposition = List.of(
                MeasurementType.BODY_MASS_INDEX, MeasurementType.FAT_FREE_MASS, MeasurementType.FAT_MASS,
                MeasurementType.BODY_FAT_PERCENTAGE, MeasurementType.BODY_WATER_PERCENTAGE,
                MeasurementType.SKELETAL_MUSCLE_MASS, MeasurementType.BASAL_METABOLIC_RATE,
                MeasurementType.FAT_FREE_MASS_PERCENTAGE, MeasurementType.SKELETAL_MUSCLE_MASS_PERCENTAGE,
                MeasurementType.BODY_WATER_MASS);

        for (MeasurementType type : bodyComposition) {
            BiometricMeasurementType biometric = BiometricMeasurementTypeMapper.fromMeasurementType(type);
            assertNotEquals(BiometricMeasurementType.UNKNOWN, biometric, type.name());
            assertEquals(type.name(), biometric.name());
            assertEquals(List.of(type), BiometricMeasurementTypeMapper.toMeasurementTypes(biometric));
        }
    }
}
