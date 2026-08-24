package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Update displayed interval for an existing detection box")
public record DetectionIntervalUpdateDTO(

        @Schema(example = "2026-05-07T07:21:31.606Z")
        OffsetDateTime startTimestamp,

        @Schema(example = "2026-05-07T07:24:37.087Z")
        OffsetDateTime endTimestamp
) {}
