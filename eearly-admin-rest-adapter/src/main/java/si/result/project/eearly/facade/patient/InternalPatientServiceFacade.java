package si.result.project.eearly.facade.patient;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import si.result.project.eearly.dto.patient.PatientLanguageDTO;
import si.result.project.eearly.port.patient.PatientService;
import si.result.spring.boot.bricks.annotation.Facade;

/**
 * Patient changes made by other eEarly services rather than by a caretaker, so there is no
 * caretaker to scope them to.
 */
@Facade
@RequiredArgsConstructor
public class InternalPatientServiceFacade {

    private final PatientService patientService;

    /** The language the patient picked in the app, sent on by the mobile backend. */
    public void changeLanguage(final UUID keycloakId, final PatientLanguageDTO patientLanguageDTO) {
        patientService.changeLanguage(keycloakId, patientLanguageDTO.language());
    }
}
