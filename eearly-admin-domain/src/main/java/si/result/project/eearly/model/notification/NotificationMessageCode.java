package si.result.project.eearly.model.notification;

/**
 * Message codes of the notifications. A notification stores the code (optionally followed by
 * ":" and a short parameter such as an algorithm name), never free text about a person.
 */
public enum NotificationMessageCode {
    ANALYSIS_READY,
    ANALYSIS_ERROR,
    ALGORITHM_EXECUTION_STARTED,
    ALGORITHM_EXECUTION_COMPLETED,
    ALGORITHM_EXECUTION_FAILED,
    PATIENT_SYNCED,
    THRESHOLD_EXCEEDED
}
