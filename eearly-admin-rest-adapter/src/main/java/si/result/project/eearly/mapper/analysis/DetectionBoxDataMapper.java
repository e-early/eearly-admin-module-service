package si.result.project.eearly.mapper.analysis;

import java.util.List;
import java.util.UUID;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;
import si.result.project.eearly.model.analysis.DetectionBoxData;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.analysis.DetectionBoxSources;

public final class DetectionBoxDataMapper {

    private DetectionBoxDataMapper() {}

    public static DetectionBoxData toData(final AnalysisDetectionDTO dto) {
        if (dto == null) {
            return null;
        }
        return new DetectionBoxData(
                dto.id(),
                dto.startTimestamp(),
                dto.endTimestamp(),
                dto.probability(),
                toDomainCaretakerFeedback(dto.caretakerFeedback()),
                toDomainSource(dto.source())
        );
    }

    public static List<DetectionBoxData> toDataList(final List<AnalysisDetectionDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        return normalizeDetectionDtos(dtos).stream().map(DetectionBoxDataMapper::toData).toList();
    }

    public static List<AnalysisDetectionDTO> normalizeDetectionDtos(final List<AnalysisDetectionDTO> raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.stream()
                .map(d -> new AnalysisDetectionDTO(
                        d.id() != null ? d.id() : UUID.randomUUID(),
                        d.startTimestamp(),
                        d.endTimestamp(),
                        d.probability(),
                        toDomainCaretakerFeedback(d.caretakerFeedback()),
                        toDomainSource(d.source())
                ))
                .toList();
    }

    static FeedbackState toDomainCaretakerFeedback(final FeedbackState feedback) {
        return feedback == null || feedback == FeedbackState.NONE ? null : feedback;
    }

    static String toDomainSource(final String source) {
        return DetectionBoxSources.resolveSource(source);
    }
}
