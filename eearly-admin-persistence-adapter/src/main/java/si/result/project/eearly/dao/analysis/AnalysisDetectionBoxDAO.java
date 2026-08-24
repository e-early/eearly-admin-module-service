package si.result.project.eearly.dao.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import si.result.project.eearly.model.analysis.AnalysisDetectionBox;
import si.result.project.eearly.port.analysis.AnalysisDetectionBoxRepository;

public interface AnalysisDetectionBoxDAO extends AnalysisDetectionBoxRepository,
        JpaRepository<AnalysisDetectionBox, UUID>, JpaSpecificationExecutor<AnalysisDetectionBox> {

    @Override
    @Query("""
            SELECT b FROM AnalysisDetectionBox b
            WHERE b.analysis.id = :analysisId AND b.isDeleted = false
            """)
    List<AnalysisDetectionBox> findByAnalysisId(@Param("analysisId") UUID analysisId);

    @Override
    Optional<AnalysisDetectionBox> findByIdAndIsDeletedFalse(UUID id);

    @Override
    @Query("""
            SELECT b FROM AnalysisDetectionBox b
            WHERE b.id = :detectionBoxId AND b.analysis.id = :analysisId AND b.isDeleted = false
            """)
    Optional<AnalysisDetectionBox> findByIdAndAnalysisId(
            @Param("detectionBoxId") UUID detectionBoxId,
            @Param("analysisId") UUID analysisId);
}
