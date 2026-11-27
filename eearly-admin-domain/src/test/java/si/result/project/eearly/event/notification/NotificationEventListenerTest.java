package si.result.project.eearly.event.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.result.project.eearly.event.notification.event.AnalysisErrorEvent;
import si.result.project.eearly.event.notification.event.AnalysisReadyEvent;
import si.result.project.eearly.event.notification.event.ExecutionCompletedEvent;
import si.result.project.eearly.event.notification.event.ExecutionFailedEvent;
import si.result.project.eearly.event.notification.event.ExecutionStartedEvent;
import si.result.project.eearly.event.notification.event.PatientSyncedEvent;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationMessageCode;
import si.result.project.eearly.model.notification.NotificationType;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.notification.NotificationService;
import si.result.project.eearly.port.notification.SseNotificationPublisher;
import si.result.project.eearly.port.patient.PatientService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    private static final String ALGORITHM = "Apnea detection";

    @Mock
    private NotificationService notificationService;

    @Mock
    private SseNotificationPublisher sseNotificationPublisher;

    @Mock
    private PatientService patientService;

    @InjectMocks
    private NotificationEventListener listener;

    private UUID patientId;
    private UUID referenceId;
    private UUID caretakerId1;
    private UUID caretakerId2;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        referenceId = UUID.randomUUID();
        caretakerId1 = UUID.randomUUID();
        caretakerId2 = UUID.randomUUID();
    }

    private void stubCaretakers(final UUID... ids) {
        final List<Caretaker> caretakers = new ArrayList<>();
        for (final UUID id : ids) {
            final Caretaker caretaker = mock(Caretaker.class);
            when(caretaker.getId()).thenReturn(id);
            caretakers.add(caretaker);
        }
        final Patient patient = mock(Patient.class);
        when(patient.getCaretakers()).thenReturn(caretakers);
        when(patientService.findById(patientId)).thenReturn(patient);
    }

    private List<CreateNotificationCommand> createdCommands(final int times) {
        final ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, times(times)).create(captor.capture());
        return captor.getAllValues();
    }

    private void assertCommand(final CreateNotificationCommand command, final NotificationType type,
            final String message, final UUID caretakerId) {
        assertThat(command.type()).isEqualTo(type);
        assertThat(command.message()).isEqualTo(message);
        assertThat(command.patientId()).isEqualTo(patientId);
        assertThat(command.caretakerId()).isEqualTo(caretakerId);
        assertThat(command.referenceId()).isEqualTo(referenceId);
    }

    /** Each caretaker of the patient gets one notification with the expected type, code and reference. */
    private void assertFansOut(final Consumer<Void> handler, final NotificationType type, final String message) {
        stubCaretakers(caretakerId1, caretakerId2);
        final Notification n1 = mock(Notification.class);
        final Notification n2 = mock(Notification.class);
        when(notificationService.create(any())).thenReturn(n1, n2);

        handler.accept(null);

        final List<CreateNotificationCommand> commands = createdCommands(2);
        assertCommand(commands.get(0), type, message, caretakerId1);
        assertCommand(commands.get(1), type, message, caretakerId2);
        verify(sseNotificationPublisher).broadcast(n1);
        verify(sseNotificationPublisher).broadcast(n2);
    }

    @Test
    @DisplayName("analysis ready: ANALYSIS notification per caretaker")
    void analysisReady() {
        assertFansOut(v -> listener.onAnalysisReady(new AnalysisReadyEvent(referenceId, patientId)),
                NotificationType.ANALYSIS, NotificationMessageCode.ANALYSIS_READY.name());
    }

    @Test
    @DisplayName("analysis error: ERROR notification per caretaker")
    void analysisError() {
        assertFansOut(v -> listener.onAnalysisError(new AnalysisErrorEvent(referenceId, patientId)),
                NotificationType.ERROR, NotificationMessageCode.ANALYSIS_ERROR.name());
    }

    @Test
    @DisplayName("execution started: ALGORITHM_EXECUTION notification with the algorithm name")
    void executionStarted() {
        assertFansOut(v -> listener.onExecutionStarted(new ExecutionStartedEvent(referenceId, patientId, ALGORITHM)),
                NotificationType.ALGORITHM_EXECUTION,
                NotificationMessageCode.ALGORITHM_EXECUTION_STARTED.name() + ":" + ALGORITHM);
    }

    @Test
    @DisplayName("execution completed: ALGORITHM_EXECUTION notification with the algorithm name")
    void executionCompleted() {
        assertFansOut(
                v -> listener.onExecutionCompleted(new ExecutionCompletedEvent(referenceId, patientId, ALGORITHM)),
                NotificationType.ALGORITHM_EXECUTION,
                NotificationMessageCode.ALGORITHM_EXECUTION_COMPLETED.name() + ":" + ALGORITHM);
    }

    @Test
    @DisplayName("execution failed: ERROR notification with the algorithm name")
    void executionFailed() {
        assertFansOut(v -> listener.onExecutionFailed(new ExecutionFailedEvent(referenceId, patientId, ALGORITHM)),
                NotificationType.ERROR,
                NotificationMessageCode.ALGORITHM_EXECUTION_FAILED.name() + ":" + ALGORITHM);
    }

    @Test
    @DisplayName("patient synced: SYNC notification for the named caretaker only, referencing the patient")
    void patientSynced() {
        final Notification created = mock(Notification.class);
        when(notificationService.create(any())).thenReturn(created);

        listener.onPatientSynced(new PatientSyncedEvent(patientId, caretakerId1));

        final CreateNotificationCommand command = createdCommands(1).getFirst();
        referenceId = patientId;
        assertCommand(command, NotificationType.SYNC, NotificationMessageCode.PATIENT_SYNCED.name(), caretakerId1);
        verify(sseNotificationPublisher).broadcast(created);
    }

    @Test
    @DisplayName("a patient without caretakers creates nothing")
    void noCaretakers() {
        stubCaretakers();

        listener.onAnalysisReady(new AnalysisReadyEvent(referenceId, patientId));

        verify(notificationService, never()).create(any());
        verify(sseNotificationPublisher, never()).broadcast(any());
    }

    @Test
    @DisplayName("a failing patient lookup is swallowed")
    void patientLookupFailureIsSwallowed() {
        when(patientService.findById(any())).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> listener.onExecutionFailed(new ExecutionFailedEvent(referenceId, patientId, ALGORITHM)))
                .doesNotThrowAnyException();
        verify(notificationService, never()).create(any());
    }

    @Test
    @DisplayName("a failing notification create is swallowed and nothing is pushed")
    void createFailureIsSwallowed() {
        stubCaretakers(caretakerId1);
        when(notificationService.create(any())).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> listener.onAnalysisReady(new AnalysisReadyEvent(referenceId, patientId)))
                .doesNotThrowAnyException();
        verify(sseNotificationPublisher, never()).broadcast(any());
    }

    @Test
    @DisplayName("a failing push is swallowed")
    void pushFailureIsSwallowed() {
        when(notificationService.create(any())).thenReturn(mock(Notification.class));
        doThrow(new RuntimeException("push error")).when(sseNotificationPublisher).broadcast(any());

        assertThatCode(() -> listener.onPatientSynced(new PatientSyncedEvent(patientId, caretakerId1)))
                .doesNotThrowAnyException();
    }
}
