package si.result.project.eearly.port.analysis;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.mapper.analysis.DetectionBoxMapper;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.analysis.DetectionBoxData;
import si.result.project.eearly.model.analysis.DetectionBoxSources;
import si.result.project.eearly.model.analysis.FeedbackState;

@Service
@RequiredArgsConstructor
public class AnalysisDetectionBoxService {

    private final AnalysisDetectionBoxRepository analysisDetectionBoxRepository;

    @Transactional(readOnly = true)
    public List<AnalysisDetectionBox> findActiveByAnalysisId(final UUID analysisId) {
        return analysisDetectionBoxRepository.findByAnalysisId(analysisId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisDetectionBox> findForFeedbacks(
            final Optional<OffsetDateTime> startDate,
            final Optional<OffsetDateTime> endDate) {
        return analysisDetectionBoxRepository.findAll(buildFeedbackSpecification(startDate, endDate));
    }

    @Transactional
    public void replaceAlgorithmDetections(
            final Analysis analysis,
            final List<DetectionBoxData> detections) {
        for (final AnalysisDetectionBox box : analysisDetectionBoxRepository.findByAnalysisId(analysis.getId())) {
            if (!DetectionBoxSources.isCaretakerSource(box.getSource())) {
                box.markDeleted();
                analysisDetectionBoxRepository.save(box);
            }
        }

        final List<DetectionBoxData> normalized = DetectionBoxMapper.normalizeDetections(detections);
        if (normalized == null || normalized.isEmpty()) {
            return;
        }

        for (final DetectionBoxData detection : normalized) {
            analysisDetectionBoxRepository.save(DetectionBoxMapper.toEntity(analysis, detection));
        }
    }

    @Transactional
    public AnalysisDetectionBox updateCaretakerFeedback(
            final UUID analysisId,
            final UUID detectionBoxId,
            final FeedbackState feedback) {
        final AnalysisDetectionBox box = analysisDetectionBoxRepository
                .findByIdAndAnalysisId(detectionBoxId, analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Detection not found for this analysis"));

        box.setCaretakerFeedback(feedback);
        box.markEdited();
        return analysisDetectionBoxRepository.save(box);
    }

    @Transactional
    public AnalysisDetectionBox updateDetectionInterval(
            final UUID analysisId,
            final UUID detectionBoxId,
            final OffsetDateTime startTimestamp,
            final OffsetDateTime endTimestamp) {
        final AnalysisDetectionBox box = analysisDetectionBoxRepository
                .findByIdAndAnalysisId(detectionBoxId, analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Detection not found for this analysis"));

        box.updateDisplayedInterval(startTimestamp, endTimestamp);
        return analysisDetectionBoxRepository.save(box);
    }

    @Transactional
    public void appendCaretakerFeedbackBoxes(
            final Analysis analysis,
            final List<AnalysisSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("selections must not be empty");
        }

        for (final AnalysisSelection selection : selections) {
            analysisDetectionBoxRepository.save(DetectionBoxMapper.toCaretakerEntity(analysis, selection));
        }
    }

    private static Specification<AnalysisDetectionBox> buildFeedbackSpecification(
            final Optional<OffsetDateTime> startDate,
            final Optional<OffsetDateTime> endDate) {
        final boolean diffLoad = startDate.isPresent() && endDate.isPresent();

        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();

            if (diffLoad) {
                final ZonedDateTime rangeStart = startDate.get().atZoneSameInstant(ZoneOffset.UTC);
                final ZonedDateTime rangeEnd = endDate.get().atZoneSameInstant(ZoneOffset.UTC);
                predicates.add(cb.between(
                        root.get("lastModifiedAt"),
                        rangeStart,
                        rangeEnd
                ));
            } else {
                predicates.add(cb.isFalse(root.get("isDeleted")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
