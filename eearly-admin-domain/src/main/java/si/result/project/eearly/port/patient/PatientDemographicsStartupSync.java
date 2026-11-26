package si.result.project.eearly.port.patient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Sends every patient's demographics to the mobile backend once, when the service has started —
 * the backfill for patients created before the sync existed, and the retry for any create or
 * update whose sync failed. Not repeated while the service runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "patient.demographics-sync", name = "on-startup", havingValue = "true", matchIfMissing = true)
public class PatientDemographicsStartupSync {

    private final PatientDemographicsSync patientDemographicsSync;

    @EventListener(ApplicationReadyEvent.class)
    public void syncAll() {
        try {
            patientDemographicsSync.syncAll();
        } catch (RuntimeException exception) {
            log.error("Patient demographics sync on startup failed", exception);
        }
    }
}
