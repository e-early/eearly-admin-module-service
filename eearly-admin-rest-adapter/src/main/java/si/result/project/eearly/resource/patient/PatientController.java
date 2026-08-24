package si.result.project.eearly.resource.patient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.patient.PatientDTO;
import si.result.project.eearly.dto.patient.PatientLiteDTO;
import si.result.project.eearly.dto.patient.PatientMeasurementRowDTO;
import si.result.project.eearly.dto.patient.PatientMinimalDTO;
import si.result.project.eearly.dto.patient.PatientOnboardingDTO;
import si.result.project.eearly.dto.patient.PatientUpsertDTO;
import si.result.project.eearly.facade.patient.PatientServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.time.OffsetDateTime;
import java.util.UUID;

@Tag(name = "Patient Controller")
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientServiceFacade patientServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<PatientLiteDTO>>> getPage(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Direction.DESC) final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(patientServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<PatientDTO>> getById(
            @PathVariable(name = "id") final UUID uuid) {
        return new ResponseDTO<>(patientServiceFacade.getById(uuid)).ok();
    }

    @Operation(
            summary = "Get patient measurements",
            description = "Returns paged patient measurements optionally filtered by an ISO 8601 date-time range.")
    @GetMapping(value = "/{id}/measurements", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<PatientMeasurementRowDTO>>> getMeasurementsById(
            @PathVariable(name = "id") final UUID uuid,
            @Parameter(
                    description = "Start of the date-time range (inclusive), ISO 8601 with offset",
                    example = "2026-01-01T00:00:00+00:00"
            )
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final OffsetDateTime startDate,
            @Parameter(
                    description = "End of the date-time range (inclusive), ISO 8601 with offset",
                    example = "2026-03-31T23:59:59+00:00"
            )
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final OffsetDateTime endDate,
            @ParameterObject @PageableDefault(size = 1000) final Pageable pageable) {
        return new ResponseDTO<>(patientServiceFacade.getMeasurementsById(
                uuid,
                pageable,
                startDate == null ? null : startDate.toString(),
                endDate == null ? null : endDate.toString())).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<PatientOnboardingDTO>> createPatient(
            @RequestBody final PatientUpsertDTO patientUpsertDTO) {
        return new ResponseDTO<>(patientServiceFacade.create(patientUpsertDTO)).created();
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<PatientMinimalDTO>> updatePatient(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final PatientUpsertDTO patientUpsertDTO) {
        return new ResponseDTO<>(patientServiceFacade.update(id, patientUpsertDTO)).ok();
    }
}
