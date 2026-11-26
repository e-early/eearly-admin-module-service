package si.result.project.eearly.resource.patient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.result.project.eearly.dto.patient.PatientLanguageDTO;
import si.result.project.eearly.facade.patient.InternalPatientServiceFacade;

/**
 * Called by other eEarly services, not the admin UI: SecurityConfig lets only the mobile backend's
 * service account (role {@code MobileService}) reach {@code /api/v1/internal/**}.
 */
@Tag(name = "Internal Patient Controller")
@RestController
@RequestMapping("/api/v1/internal/patients")
@RequiredArgsConstructor
public class InternalPatientController {

    private final InternalPatientServiceFacade internalPatientServiceFacade;

    @Operation(
            summary = "Change a patient's language",
            description = "Called by the mobile backend when the patient picks a language in the app, "
                    + "so later emails use it. The patient is identified by their Keycloak (mobile user) ID.")
    @PutMapping(value = "/{keycloakId}/language", consumes = "application/json")
    public ResponseEntity<Void> changeLanguage(
            @PathVariable("keycloakId") final UUID keycloakId,
            @RequestBody final PatientLanguageDTO patientLanguageDTO) {
        internalPatientServiceFacade.changeLanguage(keycloakId, patientLanguageDTO);
        return ResponseEntity.noContent().build();
    }
}
