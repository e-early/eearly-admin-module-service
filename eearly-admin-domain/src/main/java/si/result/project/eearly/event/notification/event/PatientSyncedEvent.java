package si.result.project.eearly.event.notification.event;

import java.util.UUID;

/**
 * Published when a patient has been synchronised from an external health record system. Core does
 * not publish it; a deployment with such an integration can.
 */
public record PatientSyncedEvent(UUID patientId, UUID caretakerId) {
}
