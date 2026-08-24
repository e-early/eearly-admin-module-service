package si.result.project.eearly.mapper.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.model.analysis.DetectionBoxData;
import si.result.project.eearly.model.analysis.FeedbackState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionBoxDataMapperTest {

    @Test
    @DisplayName("Should map detection DTO to domain data")
    void toData_ShouldMapAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2026-05-07T07:19:01Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-07T07:19:15Z");

        AnalysisDetectionDTO dto = new AnalysisDetectionDTO(
                id,
                start,
                end,
                0.15,
                FeedbackState.CONFIRMED,
                "MODEL_PREDICTION"
        );

        DetectionBoxData data = DetectionBoxDataMapper.toData(dto);

        assertEquals(id, data.id());
        assertEquals(start, data.startTimestamp());
        assertEquals(end, data.endTimestamp());
        assertEquals(0.15, data.probability());
        assertEquals(FeedbackState.CONFIRMED, data.caretakerFeedback());
        assertEquals("MODEL_PREDICTION", data.source());
    }

    @Test
    @DisplayName("Should return null when DTO is null")
    void toData_WhenNull_ShouldReturnNull() {
        assertNull(DetectionBoxDataMapper.toData(null));
    }

    @Test
    @DisplayName("Should normalize ids and keep caretaker feedback null when unset")
    void toDataList_ShouldNormalizeDetections() {
        AnalysisDetectionDTO withoutId = new AnalysisDetectionDTO(
                null,
                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                0.1,
                null,
                "MODEL_PREDICTION"
        );

        List<DetectionBoxData> result = DetectionBoxDataMapper.toDataList(List.of(withoutId));

        assertEquals(1, result.size());
        assertNotNull(result.getFirst().id());
        assertNull(result.getFirst().caretakerFeedback());
    }

    @Test
    @DisplayName("Should map API NONE caretaker feedback to null in domain data")
    void toData_WhenCaretakerFeedbackNone_ShouldReturnNull() {
        AnalysisDetectionDTO dto = new AnalysisDetectionDTO(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                0.1,
                FeedbackState.NONE,
                "MODEL_PREDICTION"
        );

        DetectionBoxData data = DetectionBoxDataMapper.toData(dto);

        assertNull(data.caretakerFeedback());
    }

    @Test
    @DisplayName("Should map legacy CARETAKER_FEEDBACK source to CARETAKER")
    void toData_WhenLegacyCaretakerFeedbackSource_ShouldMapToCaretaker() {
        AnalysisDetectionDTO dto = new AnalysisDetectionDTO(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-07T07:19:01Z"),
                OffsetDateTime.parse("2026-05-07T07:19:15Z"),
                0.1,
                null,
                "CARETAKER_FEEDBACK"
        );

        DetectionBoxData data = DetectionBoxDataMapper.toData(dto);

        assertEquals("CARETAKER", data.source());
    }

    @Test
    @DisplayName("Should return empty list for null or empty input")
    void toDataList_WhenEmpty_ShouldReturnEmptyList() {
        assertTrue(DetectionBoxDataMapper.toDataList(null).isEmpty());
        assertTrue(DetectionBoxDataMapper.toDataList(List.of()).isEmpty());
    }
}
