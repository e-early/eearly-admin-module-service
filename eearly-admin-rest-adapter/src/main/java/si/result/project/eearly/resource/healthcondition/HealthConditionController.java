package si.result.project.eearly.resource.healthcondition;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.result.project.eearly.dto.healthcondition.HealthConditionDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionLiteDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionMinimalDTO;
import si.result.project.eearly.dto.healthcondition.HealthConditionUpsertDTO;
import si.result.project.eearly.facade.healthcondition.HealthConditionServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Health Condition Controller")
@RestController
@RequestMapping("/api/v1/healthconditions")
@RequiredArgsConstructor
public class HealthConditionController {

    private final HealthConditionServiceFacade healthConditionServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<HealthConditionLiteDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(healthConditionServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<HealthConditionDTO>> getById(
            @PathVariable(name = "id") final UUID id) {
        return new ResponseDTO<>(healthConditionServiceFacade.getById(id)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<HealthConditionMinimalDTO>> createHealthCondition(
            @RequestBody final HealthConditionUpsertDTO healthConditionUpsertDTO) {
        return new ResponseDTO<>(healthConditionServiceFacade.create(healthConditionUpsertDTO)).created();
    }

}
