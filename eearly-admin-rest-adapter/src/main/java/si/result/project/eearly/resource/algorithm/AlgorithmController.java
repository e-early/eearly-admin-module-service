package si.result.project.eearly.resource.algorithm;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.algorithm.AlgorithmDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmServiceStatusDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmStatusDTO;
import si.result.project.eearly.dto.algorithm.AlgorithmUpsertDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.facade.algorithm.AlgorithmServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

import static org.springframework.data.domain.Sort.Direction.DESC;

@Tag(name = "Algorithm Controller")
@RestController
@RequestMapping("/api/v1/algorithms")
@RequiredArgsConstructor
public class AlgorithmController {

    private final AlgorithmServiceFacade algorithmServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<AlgorithmDTO>>> getPage(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = DESC) final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(algorithmServiceFacade.getPage(filter, pageable)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmDTO>> createAlgorithm(
            @RequestBody final AlgorithmUpsertDTO algorithmUpsertDTO) {
        return new ResponseDTO<>(algorithmServiceFacade.create(algorithmUpsertDTO)).created();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmDTO>> getById(
            @PathVariable UUID id) {

        return new ResponseDTO<>(algorithmServiceFacade.getById(id)).ok();
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmUpsertDTO>> updateAlgorithm(
        @PathVariable UUID id,
        @RequestBody final AlgorithmUpsertDTO algorithmUpsertDTO) {

        if (!id.equals(algorithmUpsertDTO.id())) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_NOT_FOUND);
        }

        return new ResponseDTO<>(algorithmServiceFacade.update(algorithmUpsertDTO)).ok();
    }

    @PatchMapping(value = "/{id}/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmStatusDTO>> changeStatus(
            @PathVariable UUID id,
            @RequestBody AlgorithmStatusDTO statusDTO) {

        final var updatedStatus = algorithmServiceFacade.changeStatus(id, statusDTO);

        return new ResponseDTO<>(updatedStatus).ok();
    }

    @GetMapping(value = "/{id}/service-status", produces = "application/json")
    public ResponseEntity<ResponseDTO<AlgorithmServiceStatusDTO>> getServiceStatus(
            @PathVariable UUID id) {
        return new ResponseDTO<>(algorithmServiceFacade.getServiceStatus(id)).ok();
    }
}
