package si.result.project.eearly.mapper.analysis;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import si.result.project.eearly.dto.feedback.AnalysisFeedbackPayloadDTO;
import si.result.project.eearly.dto.feedback.DetectionBoxFeedbackDTO;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.AlgorithmCategoryType;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;

@Slf4j
public final class AnalysisFeedbackMapper {

    private static final String DATASOURCE_TYPE_ADMIN_APP = "AdminApp";

    private AnalysisFeedbackMapper() {}

    public static AnalysisFeedbackPayloadDTO toFeedbackPayload(
            final Analysis analysis,
            final List<AnalysisDetectionBox> boxes) {
        final Patient patient = analysis.getPatient();
        final Algorithm algorithm = analysis.getAlgorithm();
        if (patient == null || algorithm == null) {
            log.warn("Skipping analysis {} feedback export: missing patient or algorithm", analysis.getId());
            return null;
        }

        final UUID caretakersId = resolveCaretakersId(patient);
        if (caretakersId == null) {
            log.warn("Skipping analysis {} feedback export: patient has no caretaker", analysis.getId());
            return null;
        }

        final List<DetectionBoxFeedbackDTO> detectionBoxes = boxes.stream()
                .map(AnalysisFeedbackMapper::toDetectionBoxFeedbackDto)
                .toList();

        return new AnalysisFeedbackPayloadDTO(
                analysis.getId(),
                patient.getId(),
                caretakersId,
                resolveDiagnosisType(algorithm.getCategory()),
                DATASOURCE_TYPE_ADMIN_APP,
                algorithm.getName(),
                algorithm.getAlgorithmVersion(),
                algorithm.getModelThreshold(),
                detectionBoxes
        );
    }

    public static DetectionBoxFeedbackDTO toDetectionBoxFeedbackDto(final AnalysisDetectionBox box) {
        return new DetectionBoxFeedbackDTO(
                box.getId(),
                box.getStartTimestamp(),
                box.getEndTimestamp(),
                box.getOriginalStartTimestamp(),
                box.getOriginalEndTimestamp(),
                box.getProbability(),
                AnalysisDetectionBoxMapper.toApiCaretakerFeedback(box.getCaretakerFeedback()),
                DetectionBoxSources.toFeedbackExportSource(box.getSource()),
                AnalysisDetectionBoxMapper.toOffsetDateTime(box.getCreatedAt()),
                AnalysisDetectionBoxMapper.toOffsetDateTime(box.getLastModifiedAt()),
                box.getAuditStatus()
        );
    }

    private static UUID resolveCaretakersId(final Patient patient) {
        final List<Caretaker> caretakers = patient.getCaretakers();
        if (caretakers == null || caretakers.isEmpty()) {
            return null;
        }
        return caretakers.getFirst().getId();
    }

    private static String resolveDiagnosisType(final AlgorithmCategoryType category) {
        return category != null ? category.name() : null;
    }
}
