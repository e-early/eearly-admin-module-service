package si.result.project.eearly.mapper.analysis;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.analysis.DetectionBoxSources;

public final class AnalysisDetectionBoxMapper {

    private AnalysisDetectionBoxMapper() {}

    public static DetectionBoxLists splitBySource(final List<AnalysisDetectionBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return new DetectionBoxLists(List.of(), List.of());
        }

        final List<AnalysisDetectionDTO> algorithmDetections = new ArrayList<>();
        final List<AnalysisDetectionDTO> caretakerFeedbackDetections = new ArrayList<>();

        for (final AnalysisDetectionBox box : boxes) {
            final AnalysisDetectionDTO dto = toDto(box);
            if (DetectionBoxSources.CARETAKER.equals(box.getSource())) {
                caretakerFeedbackDetections.add(dto);
            } else {
                algorithmDetections.add(dto);
            }
        }

        return new DetectionBoxLists(List.copyOf(algorithmDetections), List.copyOf(caretakerFeedbackDetections));
    }

    public static AnalysisDetectionDTO toDto(final AnalysisDetectionBox box) {
        return new AnalysisDetectionDTO(
                box.getId(),
                box.getStartTimestamp(),
                box.getEndTimestamp(),
                box.getProbability(),
                toApiCaretakerFeedback(box.getCaretakerFeedback()),
                box.getSource()
        );
    }

    public static FeedbackState toApiCaretakerFeedback(final FeedbackState feedback) {
        if (feedback == null || feedback == FeedbackState.NONE) {
            return null;
        }
        return feedback;
    }

    public static OffsetDateTime toOffsetDateTime(final ZonedDateTime value) {
        return value != null ? value.toOffsetDateTime() : null;
    }
}
