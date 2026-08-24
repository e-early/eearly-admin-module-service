package si.result.project.eearly.resource.caretaker;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.caretaker.CaretakerDTO;
import si.result.project.eearly.dto.caretaker.CaretakerLiteDTO;
import si.result.project.eearly.dto.caretaker.CaretakerUpsertDTO;
import si.result.project.eearly.facade.caretaker.CaretakerServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Caretaker Controller")
@RestController
@RequestMapping("/api/v1/caretakers")
@RequiredArgsConstructor
public class CaretakerController {
    
    private final CaretakerServiceFacade  caretakerServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<CaretakerLiteDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(caretakerServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<CaretakerDTO>> getById(
            @PathVariable(name = "id") final UUID uuid) {
        return new ResponseDTO<>(caretakerServiceFacade.getById(uuid)).ok();
    }

    @GetMapping(value = "keycloak/{keycloakId}", produces = "application/json")
    public ResponseEntity<ResponseDTO<CaretakerDTO>> getByKeycloakId(
            @PathVariable(name = "keycloakId") final UUID uuid) {
        return new ResponseDTO<>(caretakerServiceFacade.getByKeycloakId(uuid)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<CaretakerDTO>> createPatient(
            @RequestBody final CaretakerUpsertDTO caretakerUpsertDTO) {
        return new ResponseDTO<>(caretakerServiceFacade.create(caretakerUpsertDTO)).created();
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<CaretakerDTO>> updatePatient(
            @PathVariable(name = "id") final UUID id,
            @RequestBody final CaretakerUpsertDTO caretakerUpsertDTO) {
        return new ResponseDTO<>(caretakerServiceFacade.update(id, caretakerUpsertDTO)).ok();
    }
}
