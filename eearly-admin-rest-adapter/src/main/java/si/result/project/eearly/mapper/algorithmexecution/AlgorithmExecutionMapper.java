package si.result.project.eearly.mapper.algorithmexecution;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionDTO;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.exception.DomainException;

@Mapper
public interface AlgorithmExecutionMapper {

    @Mapping(target = "algorithmId", source = "algorithmExecution.algorithm.id")
    @Mapping(target = "patientId", source = "algorithmExecution.patient.id")
    @Mapping(target = "analysisId", source = "algorithmExecution.analysis.id")
    AlgorithmExecutionDTO toDTO(final AlgorithmExecution algorithmExecution);

    @Mapping(target = "algorithmId", source = "algorithmExecution.algorithm.id")
    @Mapping(target = "patientId", source = "algorithmExecution.patient.id")
    @Mapping(target = "analysisId", source = "algorithmExecution.analysis.id")
    AlgorithmExecutionUpsertDTO toUpsertDTO(final AlgorithmExecution algorithmExecution);


    @Mapping(target = "algorithm", source = "algorithm")
    @Mapping(target = "analysis", source = "analysis")
    @Mapping(target = "patient", source = "patient")
    @Mapping(target = "inputParameters", source = "dto.inputParameters")
    CreateAlgorithmExecutionCommand toCreateCommand(final AlgorithmExecutionUpsertDTO dto, final Algorithm algorithm, final Analysis analysis, final Patient patient);

    @AfterMapping
    default void validateAfterCreate(@MappingTarget CreateAlgorithmExecutionCommand command) {
        if (command.inputParameters() == null) {
           if (command.analysis() == null || command.analysis().getInputParameters() == null) {
               throw new DomainException(DomainExceptionCode.ALGORITHM_EXECUTION_INVALID_INPUT_PARAMETERS);
           }
        }
    }
}
