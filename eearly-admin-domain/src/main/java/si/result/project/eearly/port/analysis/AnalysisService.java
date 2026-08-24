package si.result.project.eearly.port.analysis;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.command.CreateAnalysisCommand;
import si.result.project.eearly.model.analysis.command.UpdateAnalysisCommand;
import si.result.spring.boot.bricks.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;

    public Analysis findById(UUID id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Analysis with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.ANALYSIS_NOT_FOUND);
                });
    }

    public Page<Analysis> findAll(Specification<Analysis> specification, Pageable pageable) {
        return analysisRepository.findAll(specification, pageable);
    }

    public Analysis update(UpdateAnalysisCommand command) {
        log.info("Updating analysis: {}", command);
        final var analysis = findById(command.getId());
        analysis.update(command);
        return analysisRepository.save(analysis);
    }

    public Analysis create(CreateAnalysisCommand command) {
        log.info("Creating analysis: {}", command);
        final var analysis = Analysis.create(command);
        log.debug("Analysis created with ID: {}", analysis.getId());
        return analysisRepository.save(analysis);
    }

    @Transactional
    public Analysis updateResult(final UUID analysisId, final AnalysisState state, final Boolean detected) {
        log.info("Updating analysis {} with state {} detected {}", analysisId, state, detected);
        final var analysis = findById(analysisId);
        analysis.applyAlgorithmResult(state, detected);
        return analysisRepository.save(analysis);
    }

    @Transactional
    public Analysis updateState(final UUID analysisId, final AnalysisState state) {
        log.info("Updating analysis {} state to {}", analysisId, state);
        final Analysis analysis = findById(analysisId);
        analysis.applyAlgorithmResult(state, analysis.getDetected());
        return analysisRepository.save(analysis);
    }
}
