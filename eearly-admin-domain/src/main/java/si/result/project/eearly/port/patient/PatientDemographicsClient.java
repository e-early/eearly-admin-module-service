package si.result.project.eearly.port.patient;

import java.time.LocalDate;
import java.util.UUID;
import si.result.project.eearly.model.gender.GenderType;

/**
 * Sends a patient's date of birth and gender to the mobile backend, which keeps its own copy for
 * body composition (it needs the patient's age and sex). The admin service owns these values.
 */
public interface PatientDemographicsClient {

    /** Throws if the mobile backend can't be reached or rejects the values. */
    void upsertDemographics(UUID keycloakId, LocalDate dateOfBirth, GenderType gender);
}
