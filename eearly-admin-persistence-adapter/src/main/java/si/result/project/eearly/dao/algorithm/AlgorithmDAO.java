package si.result.project.eearly.dao.algorithm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.port.algorithm.AlgorithmRepository;

import java.util.UUID;

@SuppressWarnings("unused")
public interface AlgorithmDAO extends AlgorithmRepository,
        JpaRepository<Algorithm, UUID>, JpaSpecificationExecutor<Algorithm> {}
