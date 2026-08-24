package si.result.project.eearly.model.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AnalysisSelection(
        OffsetDateTime startTimestamp,
        OffsetDateTime endTimestamp,
        List<UUID> measurementIds
) {
}
