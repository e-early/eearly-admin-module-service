package si.result.project.eearly.event.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import si.result.project.eearly.event.notification.event.AnalysisErrorEvent;
import si.result.project.eearly.event.notification.event.AnalysisReadyEvent;
import si.result.project.eearly.event.notification.event.ExecutionCompletedEvent;
import si.result.project.eearly.event.notification.event.ExecutionFailedEvent;
import si.result.project.eearly.event.notification.event.ExecutionStartedEvent;
import si.result.project.eearly.event.notification.event.PatientSyncedEvent;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationMessageCode;
import si.result.project.eearly.model.notification.NotificationType;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.port.notification.NotificationService;
import si.result.project.eearly.port.notification.SseNotificationPublisher;
import si.result.project.eearly.port.patient.PatientService;

import java.util.UUID;

/**
 * Turns domain events into in-app notifications for the caretakers of the patient. Runs after the
 * publishing transaction commits, in its own transaction, and never lets a failure reach the
 * publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    // Exception code and the id of the entity the event is about.
    private static final String FAILURE_LOG_FORMAT = "{} {}";

    private final NotificationService notificationService;
    private final SseNotificationPublisher sseNotificationPublisher;
    private final PatientService patientService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAnalysisReady(final AnalysisReadyEvent event) {
        notifyCaretakers(event.patientId(), NotificationType.ANALYSIS,
                NotificationMessageCode.ANALYSIS_READY.name(), event.analysisId(),
                DomainExceptionCode.NOTIFICATION_ANALYSIS_HANDLING_FAILED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAnalysisError(final AnalysisErrorEvent event) {
        notifyCaretakers(event.patientId(), NotificationType.ERROR,
                NotificationMessageCode.ANALYSIS_ERROR.name(), event.analysisId(),
                DomainExceptionCode.NOTIFICATION_ANALYSIS_HANDLING_FAILED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExecutionStarted(final ExecutionStartedEvent event) {
        notifyCaretakers(event.patientId(), NotificationType.ALGORITHM_EXECUTION,
                NotificationMessageCode.ALGORITHM_EXECUTION_STARTED.name() + ":" + event.algorithmName(),
                event.executionId(), DomainExceptionCode.NOTIFICATION_ANALYSIS_EXECUTION_HANDLING_FAILED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExecutionCompleted(final ExecutionCompletedEvent event) {
        notifyCaretakers(event.patientId(), NotificationType.ALGORITHM_EXECUTION,
                NotificationMessageCode.ALGORITHM_EXECUTION_COMPLETED.name() + ":" + event.algorithmName(),
                event.executionId(), DomainExceptionCode.NOTIFICATION_ANALYSIS_EXECUTION_HANDLING_FAILED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExecutionFailed(final ExecutionFailedEvent event) {
        notifyCaretakers(event.patientId(), NotificationType.ERROR,
                NotificationMessageCode.ALGORITHM_EXECUTION_FAILED.name() + ":" + event.algorithmName(),
                event.executionId(), DomainExceptionCode.NOTIFICATION_ANALYSIS_EXECUTION_HANDLING_FAILED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPatientSynced(final PatientSyncedEvent event) {
        try {
            final Notification notification = notificationService.create(new CreateNotificationCommand(
                    NotificationType.SYNC,
                    NotificationMessageCode.PATIENT_SYNCED.name(),
                    event.patientId(), event.caretakerId(), event.patientId()));
            sseNotificationPublisher.broadcast(notification);
        } catch (Exception e) {
            log.error(FAILURE_LOG_FORMAT, DomainExceptionCode.NOTIFICATION_PATIENT_SYNC_FAILED,
                    event.patientId(), e);
        }
    }

    private void notifyCaretakers(final UUID patientId, final NotificationType type, final String message,
            final UUID referenceId, final DomainExceptionCode failureCode) {
        try {
            for (final Caretaker caretaker : patientService.findById(patientId).getCaretakers()) {
                final Notification notification = notificationService.create(new CreateNotificationCommand(
                        type, message, patientId, caretaker.getId(), referenceId));
                sseNotificationPublisher.broadcast(notification);
            }
        } catch (Exception e) {
            log.error(FAILURE_LOG_FORMAT, failureCode, referenceId, e);
        }
    }
}
