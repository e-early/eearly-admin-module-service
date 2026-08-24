package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema
public record AnalysisSelectionDTO (

        @Schema(example = "2026-02-12T08:00:00+01:00")
        OffsetDateTime startTimestamp,

        @Schema(example = "2026-02-12T08:00:00+01:00")
        OffsetDateTime endTimestamp,

        @Schema(description = "Measurement (type) IDs selected in this interval for EHR data preparation")
        List<UUID> measurementIds

) {}
