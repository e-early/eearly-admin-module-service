package si.result.project.eearly.dto.algorithmexecution;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import si.result.project.eearly.dto.common.StringOrJsonDeserializer;

@Schema
public record AlgorithmExecutionUpsertDTO(

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID algorithmId,

        @Schema(example = "c1a7d9b2-3b2c-4a11-9b44-7f8c2e3d9a55")
        UUID patientId,

        @Schema(example = "d7e1c2f4-5b6a-4c33-8a11-1e2f3d4c5b66", nullable = true)
        UUID analysisId,

        @Schema(example = "MANUAL")
        String triggerType,

        @JsonDeserialize(using = StringOrJsonDeserializer.class)
        @Schema(
                description = "Selected analysis windows used to prepare algorithm input data",
                example = """
                        [
                          {
                            "startTimestamp": "2026-02-12T08:00:00+01:00",
                            "endTimestamp": "2026-02-12T08:01:20+01:00",
                            "measurementIds": [
                              "22222222-aaaa-bbbb-cccc-dddddddddddd",
                              "33333333-aaaa-bbbb-cccc-dddddddddddd"
                            ]
                          },
                          {
                            "startTimestamp": "2026-02-12T08:05:00+01:00",
                            "endTimestamp": "2026-02-12T08:06:00+01:00",
                            "measurementIds": [
                              "44444444-aaaa-bbbb-cccc-dddddddddddd"
                            ]
                          }
                        ]
                        """
        )
        String inputParameters

) {

}
