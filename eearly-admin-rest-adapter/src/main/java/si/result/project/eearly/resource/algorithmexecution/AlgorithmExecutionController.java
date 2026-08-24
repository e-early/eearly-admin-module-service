package si.result.project.eearly.resource.algorithmexecution;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionDTO;
import si.result.project.eearly.dto.algorithmexecution.AlgorithmExecutionUpsertDTO;
import si.result.project.eearly.facade.algorithmexecution.AlgorithmExecutionServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Algorithm Execution Controller")
@RestController
@RequestMapping("/api/v1/algorithm-executions")
@RequiredArgsConstructor
public class AlgorithmExecutionController {

    private final AlgorithmExecutionServiceFacade algorithmExecutionServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<AlgorithmExecutionDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable, @PathFilter Filter filter) {
        return new ResponseDTO<>(algorithmExecutionServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmExecutionDTO>> getById(@PathVariable UUID id) {

        return new ResponseDTO<>(algorithmExecutionServiceFacade.getById(id)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmExecutionDTO>> create(
            @RequestBody final AlgorithmExecutionUpsertDTO algorithmExecutionUpsertDTO) {
        return new ResponseDTO<>(
                algorithmExecutionServiceFacade.create(algorithmExecutionUpsertDTO)).created();
    }

    @PutMapping(value = "/{id}/cancel", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmExecutionDTO>> cancelExecution(
            @PathVariable UUID id) {
        return new ResponseDTO<>(algorithmExecutionServiceFacade.cancelExecution(id)).ok();
    }

    @GetMapping(value = "/patient/{patientId}", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<AlgorithmExecutionDTO>>> getByPatient(
            @PathVariable UUID patientId,
            @ParameterObject @PageableDefault Pageable pageable) {
        return new ResponseDTO<>(
                algorithmExecutionServiceFacade.getByPatient(patientId, pageable)).ok();
    }

    @GetMapping(value = "/algorithm/{algorithmId}", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<AlgorithmExecutionDTO>>> getByAlgorithm(
            @PathVariable UUID algorithmId,
            @ParameterObject @PageableDefault Pageable pageable) {
        return new ResponseDTO<>(
                algorithmExecutionServiceFacade.getByAlgorithm(algorithmId, pageable)).ok();
    }
}
