package si.result.project.eearly.port.algorithmexecution;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;

@Repository
public interface AlgorithmExecutionRepository {

    Optional<AlgorithmExecution> findById(UUID id);

    /**
     * Reads the execution and locks its row ({@code SELECT ... FOR UPDATE}) until the end of the transaction.
     * Used to use a callback token up exactly once, even for concurrent callbacks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ae FROM AlgorithmExecution ae WHERE ae.id = :id")
    Optional<AlgorithmExecution> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT ae
            FROM AlgorithmExecution ae
            WHERE ae.analysis.id = :analysisId
              AND ae.executionStatus IN :activeStatuses
            ORDER BY ae.createdAt DESC
            """)
    List<AlgorithmExecution> findByAnalysisIdAndExecutionStatusIn(
            @Param("analysisId") UUID analysisId,
            @Param("activeStatuses") Collection<AlgorithmExecutionStatus> activeStatuses,
            Pageable pageable
    );

    /** Executions in one of the statuses that were last modified before the cutoff. */
    @Query("""
            SELECT ae
            FROM AlgorithmExecution ae
            LEFT JOIN FETCH ae.analysis
            WHERE ae.executionStatus IN :statuses
              AND ae.lastModifiedAt < :cutoff
            """)
    List<AlgorithmExecution> findStuckExecutions(
            @Param("statuses") Collection<AlgorithmExecutionStatus> statuses,
            @Param("cutoff") ZonedDateTime cutoff
    );

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
