package si.result.project.eearly.port.patient;

import java.time.LocalDate;
import java.util.UUID;
import si.result.project.eearly.model.gender.GenderType;

/**
 * Sends a patient's date of birth, gender and language to the mobile backend, which keeps its own
 * copy: body composition needs the patient's age and sex, and the app and push notifications use
 * the patient's language. The admin service owns these values.
 */
public interface PatientDemographicsClient {

    /** Throws if the mobile backend can't be reached or rejects the values. */
    void upsertDemographics(UUID keycloakId, LocalDate dateOfBirth, GenderType gender, String language);
}
