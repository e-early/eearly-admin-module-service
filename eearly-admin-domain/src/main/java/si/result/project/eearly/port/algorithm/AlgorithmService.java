package si.result.project.eearly.port.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.AlgorithmStatusType;
import si.result.project.eearly.model.algorithm.command.CreateAlgorithmCommand;
import si.result.project.eearly.model.algorithm.command.UpdateAlgorithmCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmService {

    private final AlgorithmRepository algorithmRepository;

    public Algorithm findById(UUID id) {
        return algorithmRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Algorithm with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.ALGORITHM_NOT_FOUND);
                });
    }

    public Page<Algorithm> findAll(Specification<Algorithm> specification, Pageable pageable) {
        return algorithmRepository.findAll(specification, pageable);
    }

    public List<Algorithm> findAllById(List<UUID> ids) {
        return algorithmRepository.findAllById(ids);
    }

    public Algorithm create(CreateAlgorithmCommand command) {
        log.info("Creating algorithm: {}", command);
        final var algorithm = Algorithm.create(command);
        log.debug("Algorithm crated with ID: {}", algorithm.getId());
        return algorithmRepository.save(algorithm);
    }

    public Algorithm update(UpdateAlgorithmCommand command) {
        final var existing = algorithmRepository.findById(command.id())
                .orElseThrow(() -> new DomainException(DomainExceptionCode.ALGORITHM_NOT_FOUND));

        existing.update(command);

        return algorithmRepository.save(existing);
    }

    public Algorithm changeStatus(UUID id, AlgorithmStatusType status) {
        final var algorithm = algorithmRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Algorithm with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.ALGORITHM_NOT_FOUND);
                });

        log.info("Changing status of algorithm {} to {}", id, status);

        algorithm.setStatus(status);

        return algorithmRepository.save(algorithm);
    }

    @Transactional
    public void updateModelThreshold(final UUID algorithmId, final Double modelThreshold) {
        if (modelThreshold == null) {
            return;
        }
        final Algorithm algorithm = findById(algorithmId);
        algorithm.setModelThreshold(modelThreshold);
        algorithmRepository.save(algorithm);
    }
}
