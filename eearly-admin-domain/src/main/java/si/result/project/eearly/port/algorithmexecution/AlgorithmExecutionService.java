package si.result.project.eearly.port.algorithmexecution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmExecutionService {

    private final AlgorithmExecutionRepository algorithmExecutionRepository;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlgorithmExecution create(CreateAlgorithmExecutionCommand command) {
        log.info("Creating AlgorithmExecution: {}", command);
        final var algorithmExecution = AlgorithmExecution.create(command);
        log.debug("AlgorithmExecution crated with ID: {}", algorithmExecution.getId());
        return algorithmExecutionRepository.save(algorithmExecution);
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
        final AlgorithmExecution execution = findById(id);
        execution.setResultData(resultData);
        execution.setErrorMessage(null);
        execution.updateStatus(AlgorithmExecutionStatus.COMPLETED);
        algorithmExecutionRepository.save(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String errorMessage) {
        final AlgorithmExecution execution = findById(id);
        if (execution.getExecutionStatus() == AlgorithmExecutionStatus.COMPLETED
                || execution.getExecutionStatus() == AlgorithmExecutionStatus.CANCELLED
                || execution.getExecutionStatus() == AlgorithmExecutionStatus.FAILED) {
            return;
        }
        execution.setErrorMessage(errorMessage);
        execution.updateStatus(AlgorithmExecutionStatus.FAILED);
        algorithmExecutionRepository.save(execution);
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

}
