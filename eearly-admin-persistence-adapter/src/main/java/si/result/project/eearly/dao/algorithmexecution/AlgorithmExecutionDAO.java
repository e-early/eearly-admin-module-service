package si.result.project.eearly.dao.algorithmexecution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface AlgorithmExecutionDAO extends AlgorithmExecutionRepository,
        JpaRepository<AlgorithmExecution, UUID>, JpaSpecificationExecutor<AlgorithmExecution> {}
