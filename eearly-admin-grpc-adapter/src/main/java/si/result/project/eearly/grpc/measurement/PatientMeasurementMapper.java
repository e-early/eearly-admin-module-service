package si.result.project.eearly.grpc.measurement;

import org.mapstruct.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.result.eearly.genproto.MeasurementSubTypeEnum;
import si.result.eearly.genproto.MeasurementTypeEnum;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import si.result.project.eearly.model.patient.measurement.PatientMeasurementRow;

@Mapper(componentModel = "spring")
public interface PatientMeasurementMapper {

    Logger LOG = LoggerFactory.getLogger(PatientMeasurementMapper.class);

    default List<String> toMobileMeasurementTypes(List<Measurement> measurements) {
        return measurements.stream()
                .map(Measurement::getType)
                .map(this::toMobileMeasurementType)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    default List<String> toMobileMeasurementTypesFromBiometric(List<BiometricMeasurementType> measurementTypes) {
        return measurementTypes.stream()
                .map(this::toMobileMeasurementType)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    default List<PatientMeasurementRow> toPatientMeasurementRows(
            List<si.result.eearly.genproto.Measurement> measurements,
            Map<String, si.result.eearly.genproto.MeasurementType> mobileMeasurementTypeById) {

        return measurements.stream()
                .map(measurement -> toPatientMeasurementRow(measurement, mobileMeasurementTypeById))
                .filter(Objects::nonNull)
                .toList();
    }

    private PatientMeasurementRow toPatientMeasurementRow(
            si.result.eearly.genproto.Measurement measurement,
            Map<String, si.result.eearly.genproto.MeasurementType> mobileMeasurementTypeById) {

        String mobileMeasurementTypeId = measurement.getMeasurementTypeId();
        if (mobileMeasurementTypeId.isBlank()) {
            return null;
        }

        si.result.eearly.genproto.MeasurementType mobileMeasurementType =
                mobileMeasurementTypeById.get(mobileMeasurementTypeId);
        if (mobileMeasurementType == null) {
            LOG.warn("Skipping mobile measurements for unknown measurement type id: {}",
                    mobileMeasurementTypeId);
            return null;
        }

        Optional<MeasurementType> adminMeasurementType = toAdminMeasurementType(
                mobileMeasurementType);
        if (adminMeasurementType.isEmpty()) {
            LOG.warn("Skipping unsupported mobile measurement type: {}",
                    mobileMeasurementType.getMeasurementType());
            return null;
        }

        return new PatientMeasurementRow(
                toUuid(measurement.getMeasurementBatchId()),
                adminMeasurementType.get(),
                mobileMeasurementType.getUnit(),
                java.time.Instant.parse(measurement.getMeasuredAt()),
                measurement.getValue());
    }

    private Optional<String> toMobileMeasurementType(MeasurementType measurementType) {
        return switch (measurementType) {
            case BLOOD_PRESSURE, BLOOD_PRESSURE_SYSTOLIC, BLOOD_PRESSURE_DIASTOLIC ->
                    Optional.of("BLOOD_PRESSURE");
            case BLOOD_GLUCOSE -> Optional.of("BLOOD_GLUCOSE");
            case WEIGHT -> Optional.of("BODY_WEIGHT");
            case HEIGHT -> Optional.of("BODY_HEIGHT");
            case HEART_RATE -> Optional.of("HEART_RATE");
            case OXYGEN_SATURATION -> Optional.of("OXYGEN_SATURATION");
            case HEART_RATE_VARIABILITY -> Optional.of("HEART_RATE_VARIABILITY");
            case STRESS_LEVEL -> Optional.of("STRESS_LEVEL");
            case BODY_TEMPERATURE -> Optional.of("BODY_TEMPERATURE");
            default -> Optional.empty();
        };
    }

    private Optional<String> toMobileMeasurementType(BiometricMeasurementType measurementType) {
        return switch (measurementType) {
            case BLOOD_PRESSURE -> Optional.of("BLOOD_PRESSURE");
            case BLOOD_GLUCOSE -> Optional.of("BLOOD_GLUCOSE");
            case HEART_RATE -> Optional.of("HEART_RATE");
            case BODY_TEMPERATURE -> Optional.of("BODY_TEMPERATURE");
            case BODY_WEIGHT -> Optional.of("BODY_WEIGHT");
            case BODY_HEIGHT -> Optional.of("BODY_HEIGHT");
            case BLOOD_OXYGEN -> Optional.of("OXYGEN_SATURATION");
            default -> Optional.empty();
        };
    }

    private Optional<MeasurementType> toAdminMeasurementType(
            si.result.eearly.genproto.MeasurementType measurementType) {
        MeasurementTypeEnum type = measurementType.getMeasurementType();
        MeasurementSubTypeEnum subType = measurementType.getMeasurementSubType();

        return switch (type) {
            case BLOOD_PRESSURE -> Optional.of(switch (subType) {
                case SYSTOLIC -> MeasurementType.BLOOD_PRESSURE_SYSTOLIC;
                case DIASTOLIC -> MeasurementType.BLOOD_PRESSURE_DIASTOLIC;
                default -> MeasurementType.BLOOD_PRESSURE;
            });
            case BLOOD_GLUCOSE -> Optional.of(MeasurementType.BLOOD_GLUCOSE);
            case BODY_WEIGHT -> Optional.of(MeasurementType.WEIGHT);
            case BODY_HEIGHT -> Optional.of(MeasurementType.HEIGHT);
            case HEART_RATE -> Optional.of(MeasurementType.HEART_RATE);
            case OXYGEN_SATURATION -> Optional.of(MeasurementType.OXYGEN_SATURATION);
            case HEART_RATE_VARIABILITY -> Optional.of(MeasurementType.HEART_RATE_VARIABILITY);
            case STRESS_LEVEL -> Optional.of(MeasurementType.STRESS_LEVEL);
            case BODY_TEMPERATURE -> Optional.of(MeasurementType.BODY_TEMPERATURE);
            default -> Optional.empty();
        };
    }

    private UUID toUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
