package si.result.project.eearly.mapper.analysis;

import java.util.List;
import si.result.project.eearly.dto.analysis.AnalysisDetectionDTO;

public record DetectionBoxLists(
    List<AnalysisDetectionDTO> detections,
    List<AnalysisDetectionDTO> caretakerFeedbackDetections) {

    public DetectionBoxLists {
        detections = List.copyOf(detections);
        caretakerFeedbackDetections = List.copyOf(caretakerFeedbackDetections);
    }
}
