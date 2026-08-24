package si.result.project.eearly.dto.algorithmexecution;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema
public record AlgorithmExecutionDTO(

        @Schema(example = "9f4c2c65-6e8f-4d8d-9b91-2f3a1d0c7e11")
        UUID id,

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID algorithmId,

        @Schema(example = "c1a7d9b2-3b2c-4a11-9b44-7f8c2e3d9a55")
        UUID patientId,

        @Schema(example = "d7e1c2f4-5b6a-4c33-8a11-1e2f3d4c5b66")
        UUID analysisId,

        @Schema(example = "PENDING")
        String executionStatus,

        @Schema(example = "MANUAL")
        String triggerType,

        @Schema(example = "task-3f5e8d7c9b")
        String externalRequestId,

        @Schema(
                description = "Stored execution input selections used for EHR-based data preparation",
                example = """
                        [
                          {
                            "startTimestamp": "2026-02-12T08:00:00+01:00",
                            "endTimestamp": "2026-02-12T08:01:20+01:00",
                            "measurementIds": [
                              "22222222-aaaa-bbbb-cccc-dddddddddddd",
                              "33333333-aaaa-bbbb-cccc-dddddddddddd"
                            ]
                          }
                        ]
                        """
        )
        String inputParameters,

        @Schema(example = "{\"apneaDetected\":false}")
        String resultData,

        @Schema(example = "2026-03-01T10:15:30Z")
        Instant startedAt,

        @Schema(example = "2026-03-01T10:16:45Z")
        Instant completedAt,

        @Schema(example = "Remote execution request failed with HTTP 503")
        String errorMessage
) {

}
