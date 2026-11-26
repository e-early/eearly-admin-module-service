package si.result.project.eearly.port.patient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.Patient_;

/**
 * Keeps the mobile backend's copy of each patient's date of birth, gender and language in step
 * with the admin service's.
 *
 * <p>Best effort: a failed sync is logged rather than failing the patient's create or update, and
 * {@link #syncAll()} (run once on startup by {@link PatientDemographicsStartupSync}) sends every
 * patient again, which both fills in patients created before the sync existed and repairs any sync
 * that failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientDemographicsSync {

    static final int PAGE_SIZE = 100;

    private final PatientService patientService;
    private final PatientDemographicsClient patientDemographicsClient;

    /** Returns whether the mobile backend now has the patient's values. */
    public boolean sync(final Patient patient) {
        if (patient.getKeycloakId() == null) {
            log.debug("Patient {} has no Keycloak ID yet, not syncing demographics", patient.getId());
            return false;
        }
        try {
            patientDemographicsClient.upsertDemographics(
                    patient.getKeycloakId(), patient.getDateOfBirth(), patient.getGender(), patient.getLanguage());
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not sync demographics of patient {} to the mobile backend: {}",
                    patient.getId(), e.getMessage(), e);
            return false;
        }
    }

    /** Syncs every patient that has a mobile account; returns how many succeeded. */
    public int syncAll() {
        final Specification<Patient> withMobileAccount =
                (root, query, cb) -> cb.isNotNull(root.get(Patient_.keycloakId));
        int synced = 0;
        int total = 0;
        Page<Patient> page = patientService.findAll(
                withMobileAccount, PageRequest.of(0, PAGE_SIZE, Sort.by(Patient_.ID)));
        while (true) {
            for (final Patient patient : page.getContent()) {
                total++;
                if (sync(patient)) {
                    synced++;
                }
            }
            if (!page.hasNext()) {
                break;
            }
            page = patientService.findAll(withMobileAccount, page.nextPageable());
        }
        log.info("Synced demographics of {} of {} patients to the mobile backend", synced, total);
        return synced;
    }
}
