package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import si.result.project.eearly.model.biometric.enumeration.BiometricDataAggregationLevel;
import si.result.project.eearly.model.biometric.enumeration.BiometricMeasurementType;
import si.result.project.eearly.model.measurement.MeasurementChartType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema
public record BiometricRecordTimeSeriesDTO(
        @Schema(example = "fedcba98-7654-3210-fedc-ba9876543212")
        UUID id,

        @Schema(example = "Heart Rate")
        String measurementName,

        @Schema(example = "BLOOD_PRESSURE")
        BiometricMeasurementType measurementType,

        @Schema(example = "HEART_RATE")
        String ehrObservationId,

        @Schema(example = "NONE")
        BiometricDataAggregationLevel aggregationLevel,

        @Schema(example = "LINE")
        MeasurementChartType chartType,

        @Schema(example = "/min")
        String unit,

        @Schema(example = "22")
        Integer totalRecords,

        @Schema(example = "2025-08-06T22:03:59Z")
        ZonedDateTime firstRecordTime,

        @Schema(example = "2025-08-06T22:06:10Z")
        ZonedDateTime lastRecordTime,

        @Schema
        BiometricStatisticsDTO statistics,

        @Schema
        List<BiometricDataPointDTO> dataPoints
) {
}
