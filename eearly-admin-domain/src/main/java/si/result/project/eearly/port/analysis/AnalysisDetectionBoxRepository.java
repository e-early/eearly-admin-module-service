package si.result.project.eearly.port.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;

public interface AnalysisDetectionBoxRepository {

    AnalysisDetectionBox save(AnalysisDetectionBox box);

    List<AnalysisDetectionBox> findAll(Specification<AnalysisDetectionBox> specification);

    List<AnalysisDetectionBox> findByAnalysisId(UUID analysisId);

    Optional<AnalysisDetectionBox> findByIdAndIsDeletedFalse(UUID id);

    Optional<AnalysisDetectionBox> findByIdAndAnalysisId(UUID detectionBoxId, UUID analysisId);
}
