package si.result.project.eearly.port.healthcondition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.model.healthcondition.command.CreateHealthConditionCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthConditionService {

    private final HealthConditionRepository healthConditionRepository;

    public Page<HealthCondition> findAll(final Specification<HealthCondition> specification, final Pageable pageable) {
        return healthConditionRepository.findAll(specification, pageable);
    }

    public List<HealthCondition> findAllById(final List<UUID> ids) {
        return healthConditionRepository.findAllById(ids);
    }

    public HealthCondition findById(final UUID id) {
        return healthConditionRepository.findById(id).orElseThrow(() -> {
            log.error("Health condition with ID {} not found", id);
            return new DomainException(DomainExceptionCode.HEALTH_CONDITION_NOT_FOUND);
        });
    }

    public HealthCondition create(final CreateHealthConditionCommand command) {
        log.info("Creating health condition: {}", command);
        final var healthCondition = HealthCondition.create(command);
        final var savedHealthCondition = healthConditionRepository.save(healthCondition);
        log.debug("Health condition created with ID: {}", savedHealthCondition.getId());
        return savedHealthCondition;
    }
}
