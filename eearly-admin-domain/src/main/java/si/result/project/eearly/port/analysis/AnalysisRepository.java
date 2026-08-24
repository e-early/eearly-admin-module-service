package si.result.project.eearly.port.analysis;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import si.result.project.eearly.model.analysis.Analysis;

public interface AnalysisRepository {

    Page<Analysis> findAll(Specification<Analysis> specification, Pageable pageable);

    Optional<Analysis> findById(UUID id);

    Analysis save(Analysis analysis);

}
