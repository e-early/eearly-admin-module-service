package si.result.project.eearly.resource.department;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.result.project.eearly.dto.department.DepartmentDTO;
import si.result.project.eearly.dto.department.DepartmentLiteDTO;
import si.result.project.eearly.dto.department.DepartmentUpsertDTO;
import si.result.project.eearly.facade.department.DepartmentServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Department Controller")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentServiceFacade departmentServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<DepartmentLiteDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(departmentServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<DepartmentDTO>> getById(
            @PathVariable(name = "id") final UUID id) {
        return new ResponseDTO<>(departmentServiceFacade.getById(id)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<DepartmentLiteDTO>> createDepartment(
            @RequestBody final DepartmentUpsertDTO departmentUpsertDTO) {
        return new ResponseDTO<>(departmentServiceFacade.create(departmentUpsertDTO)).created();
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<DepartmentLiteDTO>> updateDepartment(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final DepartmentUpsertDTO departmentUpsertDTO) {
        return new ResponseDTO<>(departmentServiceFacade.update(id, departmentUpsertDTO)).ok();
    }
}
