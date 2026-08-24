package si.result.project.eearly.mapper.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.analysis.DetectionBoxSources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisDetectionBoxMapperTest {

    @Test
    @DisplayName("Should map NONE caretaker feedback to null in API DTO")
    void toDto_WhenCaretakerFeedbackNone_ShouldReturnNull() {
        AnalysisDetectionBox box = mockBox(
                FeedbackState.NONE,
                "MODEL_PREDICTION",
                UUID.randomUUID()
        );

        AnalysisDetectionDTO dto = AnalysisDetectionBoxMapper.toDto(box);

        assertNull(dto.caretakerFeedback());
    }

    @Test
    @DisplayName("Should split algorithm and caretaker feedback detections by source")
    void splitBySource_ShouldPartitionDetections() {
        AnalysisDetectionBox algorithmBox = mockBox(
                null,
                "MODEL_PREDICTION",
                UUID.randomUUID()
        );
        AnalysisDetectionBox caretakerBox = mockBox(
                null,
                DetectionBoxSources.CARETAKER,
                UUID.randomUUID()
        );

        DetectionBoxLists lists =
                AnalysisDetectionBoxMapper.splitBySource(List.of(algorithmBox, caretakerBox));

        assertEquals(1, lists.detections().size());
        assertEquals(1, lists.caretakerFeedbackDetections().size());
        assertEquals("MODEL_PREDICTION", lists.detections().getFirst().source());
        assertEquals(
                DetectionBoxSources.CARETAKER,
                lists.caretakerFeedbackDetections().getFirst().source()
        );
    }

    @Test
    @DisplayName("Should preserve confirmed caretaker feedback in API DTO")
    void toDto_WhenCaretakerFeedbackConfirmed_ShouldReturnConfirmed() {
        AnalysisDetectionBox box = mockBox(
                FeedbackState.CONFIRMED,
                "MODEL_PREDICTION",
                UUID.randomUUID()
        );

        AnalysisDetectionDTO dto = AnalysisDetectionBoxMapper.toDto(box);

        assertEquals(FeedbackState.CONFIRMED, dto.caretakerFeedback());
    }

    private static AnalysisDetectionBox mockBox(
            final FeedbackState caretakerFeedback,
            final String source,
            final UUID id) {
        AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
        OffsetDateTime start = OffsetDateTime.parse("2026-05-07T07:19:01Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-07T07:19:15Z");

        when(box.getId()).thenReturn(id);
        when(box.getStartTimestamp()).thenReturn(start);
        when(box.getEndTimestamp()).thenReturn(end);
        when(box.getProbability()).thenReturn(0.15);
        when(box.getCaretakerFeedback()).thenReturn(caretakerFeedback);
        when(box.getSource()).thenReturn(source);
        return box;
    }
}
