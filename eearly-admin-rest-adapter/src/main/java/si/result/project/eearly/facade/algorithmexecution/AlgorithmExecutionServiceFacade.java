package si.result.project.eearly.facade.algorithmexecution;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionDTO;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.algorithmexecution.AlgorithmExecutionMapper;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.AlgorithmStatusType;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.project.eearly.port.container.AlgorithmContainerService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;

import java.util.UUID;
import si.result.spring.boot.bricks.exception.DomainException;

@Facade
@RequiredArgsConstructor
public class AlgorithmExecutionServiceFacade {

    private final AlgorithmExecutionService algorithmExecutionService;
    private final AlgorithmService algorithmService;
    private final PatientService patientService;
    private final AnalysisService analysisService;
    private final AlgorithmExecutionMapper algorithmExecutionMapper;
    private final AlgorithmContainerService algorithmContainerService;

    @Transactional(readOnly = true)
    public PagedModel<AlgorithmExecutionDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                algorithmExecutionService.findAll(filter.toSpecification(), pageable)
                        .map(algorithmExecutionMapper::toDTO)
        );
    }

    @Transactional(readOnly = true)
    public AlgorithmExecutionDTO getById(UUID id) {
        return algorithmExecutionMapper.toDTO(
                algorithmExecutionService.findById(id)
        );
    }

    @Transactional
    public AlgorithmExecutionDTO create(
            final AlgorithmExecutionUpsertDTO algorithmExecutionUpsertDTO) {
        if (algorithmExecutionUpsertDTO.algorithmId() == null) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_ID_NOT_PROVIDED);
        }
        if (algorithmExecutionUpsertDTO.patientId() == null) {
            throw new DomainException(DomainExceptionCode.PATIENT_ID_NOT_PROVIDED);
        }

        final Algorithm algorithm = algorithmService.findById(
                algorithmExecutionUpsertDTO.algorithmId());

        if (algorithm.getStatus() != AlgorithmStatusType.ACTIVE) {
            throw new DomainException(
                    DomainExceptionCode.ALGORITHM_NOT_ACTIVE
            );
        }
        final Patient patient = patientService.findById(
                algorithmExecutionUpsertDTO.patientId());

        Analysis analysis = null;
        if (algorithmExecutionUpsertDTO.analysisId() != null) {
            analysis = analysisService.findById(algorithmExecutionUpsertDTO.analysisId());
        }

        final var createCommand = algorithmExecutionMapper.toCreateCommand(
                algorithmExecutionUpsertDTO, algorithm, analysis, patient);

        final var algorithmExecution = algorithmExecutionService.create(createCommand);
        algorithmContainerService.run(algorithmExecution);

        return algorithmExecutionMapper.toDTO(algorithmExecution);
    }

    public AlgorithmExecutionDTO cancelExecution(UUID id) {
        final var execution = algorithmExecutionService.findById(id);
        algorithmContainerService.cancel(execution);
        final var updatedExecution = algorithmExecutionService.findById(id);
        return algorithmExecutionMapper.toDTO(updatedExecution);
    }

    public PagedModel<AlgorithmExecutionDTO> getByPatient(UUID patientId, Pageable pageable) {
        return new PagedModel<>(
                algorithmExecutionService.findByPatient(patientId, pageable)
                        .map(algorithmExecutionMapper::toDTO)
        );
    }

    public PagedModel<AlgorithmExecutionDTO> getByAlgorithm(UUID algorithmId, Pageable pageable) {
        return new PagedModel<>(
                algorithmExecutionService.findByAlgorithm(algorithmId, pageable)
                        .map(algorithmExecutionMapper::toDTO)
        );
    }
}
