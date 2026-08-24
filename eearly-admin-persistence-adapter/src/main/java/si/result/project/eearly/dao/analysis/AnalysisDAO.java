package si.result.project.eearly.dao.analysis;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.port.analysis.AnalysisRepository;

public interface AnalysisDAO extends AnalysisRepository,
        JpaRepository<Analysis, UUID>, JpaSpecificationExecutor<Analysis> {
}
