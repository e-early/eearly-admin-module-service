package si.result.project.eearly.port.algorithmexecution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;

@Repository
public interface AlgorithmExecutionRepository {

    Optional<AlgorithmExecution> findById(UUID id);

    @Query("""
            SELECT DISTINCT ae
            FROM AlgorithmExecution ae
            JOIN FETCH ae.algorithm algorithm
            LEFT JOIN FETCH algorithm.measurements
            JOIN FETCH ae.patient
            LEFT JOIN FETCH ae.analysis
            WHERE ae.id = :id
            """)
    Optional<AlgorithmExecution> findExecutionContextById(@Param("id") UUID id);

    @Query("""
            SELECT ae
            FROM AlgorithmExecution ae
            WHERE ae.patient.id = :patientId
            """)
    Page<AlgorithmExecution> findByPatientId(@Param("patientId") UUID patientId, Pageable pageable);

    @Query("""
            SELECT ae
            FROM AlgorithmExecution ae
            WHERE ae.algorithm.id = :algorithmId
            """)
    Page<AlgorithmExecution> findByAlgorithmId(@Param("algorithmId") UUID algorithmId,
            Pageable pageable);

    Page<AlgorithmExecution> findAll(final Specification<AlgorithmExecution> specification,
            Pageable pageable);

    AlgorithmExecution save(AlgorithmExecution algorithm);

}
