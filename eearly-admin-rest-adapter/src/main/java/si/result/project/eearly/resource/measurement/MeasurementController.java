package si.result.project.eearly.resource.measurement;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.measurement.MeasurementDTO;
import si.result.project.eearly.facade.measurement.MeasurementServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Measurement Controller")
@RestController
@RequestMapping("/api/v1/measurements")
@RequiredArgsConstructor
public class MeasurementController {

    private final MeasurementServiceFacade  measurementServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<MeasurementDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(measurementServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<MeasurementDTO>> getById(
            @PathVariable(name = "id") final UUID uuid) {
        return new ResponseDTO<>(measurementServiceFacade.getById(uuid)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<MeasurementDTO>> createMeasurement(
            @RequestBody final MeasurementDTO measurementDTO) {
        return new ResponseDTO<>(measurementServiceFacade.create(measurementDTO)).ok();
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<MeasurementDTO>> updateMeasurement(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final MeasurementDTO measurementDTO) {
        return new ResponseDTO<>(measurementServiceFacade.update(id, measurementDTO)).ok();
    }
}
