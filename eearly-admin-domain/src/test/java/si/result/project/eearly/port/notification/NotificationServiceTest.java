package si.result.project.eearly.port.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationRead;
import si.result.project.eearly.model.notification.NotificationType;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.model.notification.command.CreateNotificationReadCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationReadRepository notificationReadRepository;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID caretakerId = UUID.randomUUID();
    private final UUID otherCaretakerId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    private Notification notification(final UUID addressedTo) {
        final Notification notification = Notification.create(
                new CreateNotificationCommand(NotificationType.SYNC, "X", null, addressedTo, null));
        ReflectionTestUtils.setField(notification, "id", notificationId);
        return notification;
    }

    @Test
    @DisplayName("marks a notification addressed to the caretaker as read for that caretaker")
    void marksOwnNotificationAsRead() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification(caretakerId)));

        notificationService.markNotificationAsRead(new CreateNotificationReadCommand(caretakerId, notificationId));

        final ArgumentCaptor<NotificationRead> captor = ArgumentCaptor.forClass(NotificationRead.class);
        verify(notificationReadRepository).save(captor.capture());
        assertThat(captor.getValue().getId().getCaretakerId()).isEqualTo(caretakerId);
        assertThat(captor.getValue().getId().getNotificationId()).isEqualTo(notificationId);
    }

    @Test
    @DisplayName("marks a notification addressed to everyone as read")
    void marksBroadcastNotificationAsRead() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification(null)));

        notificationService.markNotificationAsRead(new CreateNotificationReadCommand(caretakerId, notificationId));

        verify(notificationReadRepository).save(any(NotificationRead.class));
    }

    @Test
    @DisplayName("reports a notification of another caretaker as not found and stores nothing")
    void refusesNotificationOfAnotherCaretaker() {
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification(otherCaretakerId)));

        assertThatThrownBy(() -> notificationService.markNotificationAsRead(
                new CreateNotificationReadCommand(caretakerId, notificationId)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.NOTIFICATION_NOT_FOUND));
        verify(notificationReadRepository, never()).save(any());
    }

    @Test
    @DisplayName("reports an unknown notification as not found")
    void refusesUnknownNotification() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markNotificationAsRead(
                new CreateNotificationReadCommand(caretakerId, notificationId)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("marks every unread notification of the caretaker as read for that caretaker")
    void marksAllAsRead() {
        final Notification first = mock(Notification.class);
        final Notification second = mock(Notification.class);
        when(first.getId()).thenReturn(UUID.randomUUID());
        when(second.getId()).thenReturn(UUID.randomUUID());
        when(notificationRepository.getUnreadNotificationsForCaretaker(caretakerId))
                .thenReturn(List.of(first, second));

        notificationService.markAllNotificationsAsRead(caretakerId);

        final ArgumentCaptor<NotificationRead> captor = ArgumentCaptor.forClass(NotificationRead.class);
        verify(notificationReadRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(
                read -> assertThat(read.getId().getCaretakerId()).isEqualTo(caretakerId));
    }

    @Test
    @DisplayName("creates and stores a notification from the command")
    void createsNotification() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        final UUID patientId = UUID.randomUUID();

        final Notification created = notificationService.create(new CreateNotificationCommand(
                NotificationType.ANALYSIS, "ANALYSIS_READY", patientId, caretakerId, UUID.randomUUID()));

        assertThat(created.getType()).isEqualTo(NotificationType.ANALYSIS);
        assertThat(created.getMessage()).isEqualTo("ANALYSIS_READY");
        assertThat(created.getPatientId()).isEqualTo(patientId);
        assertThat(created.getCaretakerId()).isEqualTo(caretakerId);
    }
}
