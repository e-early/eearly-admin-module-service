package si.result.project.eearly.port.algorithmexecution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.event.notification.event.ExecutionCompletedEvent;
import si.result.project.eearly.event.notification.event.ExecutionFailedEvent;
import si.result.project.eearly.event.notification.event.ExecutionStartedEvent;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmExecutionService {

    private static final List<AlgorithmExecutionStatus> ACTIVE_STATUSES = List.of(
            AlgorithmExecutionStatus.PENDING,
            AlgorithmExecutionStatus.SUBMITTED,
            AlgorithmExecutionStatus.RUNNING
    );

    private final AlgorithmExecutionRepository algorithmExecutionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AlgorithmExecution findById(UUID id) {
        return algorithmExecutionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("AlgorithmExecution with ID {} not found", id);
                    return new DomainException(
                            DomainExceptionCode.ALGORITHM_EXECUTION_NOT_FOUND);
                });
    }

    @Transactional(readOnly = true)
    public AlgorithmExecution findInitializedById(final UUID id) {

        final AlgorithmExecution execution = algorithmExecutionRepository.findExecutionContextById(id)
                .orElseThrow(() -> {
                    log.error("AlgorithmExecution with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.ALGORITHM_EXECUTION_NOT_FOUND);
                });

        if (execution.getPatient() != null && execution.getPatient().getCaretakers() != null) {
            execution.getPatient().getCaretakers().size();
        }

        return execution;
    }

    public Page<AlgorithmExecution> findByPatient(UUID patientId, Pageable pageable) {
        return algorithmExecutionRepository.findByPatientId(patientId, pageable);
    }

    public Page<AlgorithmExecution> findByAlgorithm(UUID algorithmId, Pageable pageable) {
        return algorithmExecutionRepository.findByAlgorithmId(algorithmId, pageable);
    }

    public Page<AlgorithmExecution> findAll(Specification<AlgorithmExecution> specification,
            Pageable pageable) {
        return algorithmExecutionRepository.findAll(specification, pageable);
    }

    /** The most recently created execution of the analysis that has not ended yet, if any. */
    @Transactional(readOnly = true)
    public Optional<AlgorithmExecution> findLatestActiveByAnalysisId(final UUID analysisId) {
        final List<AlgorithmExecution> active = algorithmExecutionRepository.findByAnalysisIdAndExecutionStatusIn(
                analysisId,
                ACTIVE_STATUSES,
                PageRequest.of(0, 1)
        );
        return active.isEmpty() ? Optional.empty() : Optional.of(active.getFirst());
    }

    /** Executions in one of the statuses that were last modified before the cutoff, with their analysis. */
    @Transactional(readOnly = true)
    public List<AlgorithmExecution> findStuckExecutions(
            final Collection<AlgorithmExecutionStatus> statuses, final ZonedDateTime cutoff) {
        return algorithmExecutionRepository.findStuckExecutions(statuses, cutoff);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlgorithmExecution create(CreateAlgorithmExecutionCommand command) {
        log.info("Creating AlgorithmExecution: {}", command);
        final var algorithmExecution = AlgorithmExecution.create(command);
        log.debug("AlgorithmExecution crated with ID: {}", algorithmExecution.getId());
        final AlgorithmExecution savedExecution = algorithmExecutionRepository.save(algorithmExecution);
        if (savedExecution.getTriggerType() == AlgorithmExecutionTriggerType.SCHEDULED
                && savedExecution.getPatient() != null) {
            eventPublisher.publishEvent(new ExecutionStartedEvent(
                    savedExecution.getId(),
                    savedExecution.getPatient().getId(),
                    resolveAlgorithmName(savedExecution)));
        }
        return savedExecution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlgorithmExecution save(AlgorithmExecution algorithmExecution) {
        return algorithmExecutionRepository.save(algorithmExecution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID id, AlgorithmExecutionStatus newStatus) {
        final AlgorithmExecution execution = findById(id);

        log.info("Updating AlgorithmExecution {} status: {} -> {}",
                id, execution.getExecutionStatus(), newStatus);

        execution.updateStatus(newStatus);

        algorithmExecutionRepository.save(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID id, String resultData) {
        complete(findById(id), resultData);
    }

    /**
     * Completes the execution as {@link #markCompleted(UUID, String)} does, but in the <em>caller's</em>
     * transaction (no new one is started). For the result callback, whose processing and use of the callback token
     * must commit together: a separate transaction here would wait for the row lock the token's use holds.
     * An execution that has not reached RUNNING yet is moved there first.
     */
    @Transactional
    public void completeFromCallback(final UUID id, final String resultData) {
        final AlgorithmExecution execution = findById(id);
        advanceToRunning(execution);
        complete(execution, resultData);
    }

    /** Fails the execution as {@link #markFailed(UUID, String)} does, in the caller's transaction. */
    @Transactional
    public void failFromCallback(final UUID id, final String errorMessage) {
        fail(id, findById(id), errorMessage);
    }

    private void advanceToRunning(final AlgorithmExecution execution) {
        if (execution.getExecutionStatus() == AlgorithmExecutionStatus.PENDING) {
            execution.updateStatus(AlgorithmExecutionStatus.SUBMITTED);
        }
        if (execution.getExecutionStatus() == AlgorithmExecutionStatus.SUBMITTED) {
            execution.updateStatus(AlgorithmExecutionStatus.RUNNING);
        }
    }

    private void complete(final AlgorithmExecution execution, final String resultData) {
        execution.setResultData(resultData);
        execution.setErrorMessage(null);
        execution.updateStatus(AlgorithmExecutionStatus.COMPLETED);
        final AlgorithmExecution savedExecution = algorithmExecutionRepository.save(execution);
        if (savedExecution.getTriggerType() == AlgorithmExecutionTriggerType.SCHEDULED
                && savedExecution.getPatient() != null
                && savedExecution.getAnalysis() == null) {
            eventPublisher.publishEvent(new ExecutionCompletedEvent(
                    savedExecution.getId(),
                    savedExecution.getPatient().getId(),
                    resolveAlgorithmName(savedExecution)));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String errorMessage) {
        fail(id, findById(id), errorMessage);
    }

    private void fail(final UUID id, final AlgorithmExecution execution, final String errorMessage) {
        if (execution.getExecutionStatus() == AlgorithmExecutionStatus.COMPLETED
                || execution.getExecutionStatus() == AlgorithmExecutionStatus.CANCELLED
                || execution.getExecutionStatus() == AlgorithmExecutionStatus.FAILED) {
            return;
        }
        execution.setErrorMessage(errorMessage);
        execution.updateStatus(AlgorithmExecutionStatus.FAILED);
        final AlgorithmExecution savedExecution = algorithmExecutionRepository.save(execution);
        if (savedExecution.getPatient() != null) {
            eventPublisher.publishEvent(new ExecutionFailedEvent(
                    id,
                    savedExecution.getPatient().getId(),
                    resolveAlgorithmName(savedExecution)));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(UUID id) {
        final AlgorithmExecution execution = findById(id);
        if (execution.getExecutionStatus() == AlgorithmExecutionStatus.CANCELLED) {
            return;
        }
        execution.updateStatus(AlgorithmExecutionStatus.CANCELLED);
        algorithmExecutionRepository.save(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRemoteTaskStarted(final UUID id, final String taskId) {
        final AlgorithmExecution execution = findById(id);
        execution.setExternalRequestId(taskId);
        algorithmExecutionRepository.save(execution);
    }

    private static String resolveAlgorithmName(final AlgorithmExecution execution) {
        return execution.getAlgorithm() != null && execution.getAlgorithm().getName() != null
                ? execution.getAlgorithm().getName()
                : "Algorithm";
    }

}
