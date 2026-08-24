package si.result.project.eearly.dto.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

@Builder
@Schema
public record BiometricRecordBatchDTO(
        @Schema(example = "fedcba98-7654-3210-fedc-ba9876543212")
        UUID patientId,
        @Schema(description = "Biometric response keyed by measurement type")
        Map<String, BiometricRecordTimeSeriesDTO> measurements
) {
}
