package si.result.project.eearly.mapper.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.analysis.DetectionBoxData;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.model.analysis.FeedbackState;

public final class DetectionBoxMapper {

    private DetectionBoxMapper() {}

    public static List<DetectionBoxData> normalizeDetections(final List<DetectionBoxData> raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        final List<DetectionBoxData> normalized = new ArrayList<>(raw.size());
        for (final DetectionBoxData detection : raw) {
            normalized.add(normalizeDetection(detection));
        }
        return List.copyOf(normalized);
    }

    public static DetectionBoxData normalizeDetection(final DetectionBoxData detection) {
        if (detection == null) {
            return null;
        }
        return new DetectionBoxData(
                detection.id() != null ? detection.id() : UUID.randomUUID(),
                detection.startTimestamp(),
                detection.endTimestamp(),
                detection.probability(),
                normalizeCaretakerFeedback(detection.caretakerFeedback()),
                DetectionBoxSources.resolveSource(detection.source())
        );
    }

    public static AnalysisDetectionBox toEntity(final Analysis analysis, final DetectionBoxData detection) {
        return AnalysisDetectionBox.create(
                analysis,
                detection.startTimestamp(),
                detection.endTimestamp(),
                detection.probability(),
                detection.caretakerFeedback(),
                detection.source()
        );
    }

    public static AnalysisDetectionBox toCaretakerEntity(
            final Analysis analysis,
            final AnalysisSelection selection) {
        return AnalysisDetectionBox.create(
                analysis,
                selection.startTimestamp(),
                selection.endTimestamp(),
                1.0,
                FeedbackState.CONFIRMED,
                DetectionBoxSources.CARETAKER
        );
    }

    private static FeedbackState normalizeCaretakerFeedback(final FeedbackState feedback) {
        return feedback == null || feedback == FeedbackState.NONE ? null : feedback;
    }
}
