package si.result.project.eearly.port.healthcondition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.healthcondition.HealthCondition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthConditionRepository {

    List<HealthCondition> findAllById(Iterable<UUID> ids);

    Page<HealthCondition> findAll(Specification<HealthCondition> specification, Pageable pageable);

    Optional<HealthCondition> findById(UUID id);

    HealthCondition save(HealthCondition healthCondition);
}
