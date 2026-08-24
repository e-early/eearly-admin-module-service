package si.result.project.eearly.mapper.analysis;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.dto.feedback.DetectionBoxFeedbackDTO;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.DetectionBoxSources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisFeedbackMapperTest {

    @Test
    @DisplayName("Should map algorithm box source to MODEL_PREDICTION in ML payload")
    void toDetectionBoxFeedbackDto_ShouldMapAlgorithmToModelPrediction() {
        AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
        when(box.getId()).thenReturn(UUID.randomUUID());
        when(box.getStartTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:01Z"));
        when(box.getEndTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:15Z"));
        when(box.getOriginalStartTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:01Z"));
        when(box.getOriginalEndTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:15Z"));
        when(box.getProbability()).thenReturn(0.15);
        when(box.getCaretakerFeedback()).thenReturn(null);
        when(box.getSource()).thenReturn(DetectionBoxSources.MODEL_PREDICTION);
        when(box.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-05-07T08:00:00Z").atZoneSameInstant(ZoneOffset.UTC));
        when(box.getLastModifiedAt()).thenReturn(OffsetDateTime.parse("2026-05-07T09:00:00Z").atZoneSameInstant(ZoneOffset.UTC));
        when(box.getAuditStatus()).thenReturn(si.result.project.eearly.model.analysis.DetectionBoxAuditStatus.CREATED);

        DetectionBoxFeedbackDTO dto = AnalysisFeedbackMapper.toDetectionBoxFeedbackDto(box);

        assertEquals(DetectionBoxSources.MODEL_PREDICTION, dto.source());
    }

    @Test
    @DisplayName("Should map legacy CARETAKER_FEEDBACK source to CARETAKER in ML payload")
    void toDetectionBoxFeedbackDto_ShouldResolveLegacyCaretakerSource() {
        AnalysisDetectionBox box = mock(AnalysisDetectionBox.class);
        when(box.getId()).thenReturn(UUID.randomUUID());
        when(box.getStartTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:01Z"));
        when(box.getEndTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:15Z"));
        when(box.getOriginalStartTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:01Z"));
        when(box.getOriginalEndTimestamp()).thenReturn(OffsetDateTime.parse("2026-05-07T07:19:15Z"));
        when(box.getProbability()).thenReturn(null);
        when(box.getCaretakerFeedback()).thenReturn(null);
        when(box.getSource()).thenReturn("CARETAKER_FEEDBACK");
        when(box.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-05-07T08:00:00Z").atZoneSameInstant(ZoneOffset.UTC));
        when(box.getLastModifiedAt()).thenReturn(OffsetDateTime.parse("2026-05-07T09:00:00Z").atZoneSameInstant(ZoneOffset.UTC));
        when(box.getAuditStatus()).thenReturn(si.result.project.eearly.model.analysis.DetectionBoxAuditStatus.CREATED);

        DetectionBoxFeedbackDTO dto = AnalysisFeedbackMapper.toDetectionBoxFeedbackDto(box);

        assertEquals(DetectionBoxSources.CARETAKER, dto.source());
    }
}
