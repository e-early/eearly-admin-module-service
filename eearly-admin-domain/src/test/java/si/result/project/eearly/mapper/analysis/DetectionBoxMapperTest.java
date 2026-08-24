package si.result.project.eearly.mapper.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.model.analysis.DetectionBoxData;
import si.result.project.eearly.model.analysis.DetectionBoxSources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DetectionBoxMapperTest {

    @Test
    @DisplayName("Should assign id and leave caretaker feedback null when unset")
    void normalizeDetections_ShouldAssignDefaults() {
        DetectionBoxData raw = new DetectionBoxData(
                null,
                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                0.1,
                null,
                DetectionBoxSources.MODEL_PREDICTION
        );

        List<DetectionBoxData> normalized = DetectionBoxMapper.normalizeDetections(List.of(raw));

        assertEquals(1, normalized.size());
        assertNotNull(normalized.getFirst().id());
        assertNull(normalized.getFirst().caretakerFeedback());
    }

    @Test
    @DisplayName("Should map legacy ALGORITHM source to MODEL_PREDICTION")
    void normalizeDetection_ShouldResolveLegacyAlgorithmSource() {
        DetectionBoxData raw = new DetectionBoxData(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                0.1,
                null,
                "ALGORITHM"
        );

        DetectionBoxData normalized = DetectionBoxMapper.normalizeDetection(raw);

        assertEquals(DetectionBoxSources.MODEL_PREDICTION, normalized.source());
    }

    @Test
    @DisplayName("Should map legacy CARETAKER_FEEDBACK source to CARETAKER")
    void normalizeDetection_ShouldResolveLegacyCaretakerSource() {
        DetectionBoxData raw = new DetectionBoxData(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                0.1,
                null,
                "CARETAKER_FEEDBACK"
        );

        DetectionBoxData normalized = DetectionBoxMapper.normalizeDetection(raw);

        assertEquals(DetectionBoxSources.CARETAKER, normalized.source());
    }
}
