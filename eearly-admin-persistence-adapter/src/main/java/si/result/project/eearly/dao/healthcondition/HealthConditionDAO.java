package si.result.project.eearly.dao.healthcondition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.port.healthcondition.HealthConditionRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface HealthConditionDAO extends HealthConditionRepository,
        JpaRepository<HealthCondition, UUID>, JpaSpecificationExecutor<HealthCondition> {}
