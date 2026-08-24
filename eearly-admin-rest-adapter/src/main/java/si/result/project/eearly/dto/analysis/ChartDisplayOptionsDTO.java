package si.result.project.eearly.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema
public record ChartDisplayOptionsDTO(

        @Schema(example = "ALL", description = "Time interval id, e.g. ALL, TODAY, LAST_WEEK")
        String timeInterval,

        @Schema(example = "NONE", description = "Aggregation level id, e.g. NONE, HOUR, DAY")
        String aggregationLevel,

        @Schema(description = "Series names hidden in the legend at creation time")
        List<String> hiddenSeriesNames
) {}
