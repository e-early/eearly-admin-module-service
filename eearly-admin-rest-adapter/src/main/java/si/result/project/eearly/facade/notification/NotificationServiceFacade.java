package si.result.project.eearly.facade.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.notification.NotificationDTO;
import si.result.project.eearly.dto.notification.UnreadNotificationsDTO;
import si.result.project.eearly.mapper.notification.NotificationMapper;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.notification.NotificationService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;

import java.util.UUID;

/**
 * Notifications of the authenticated caretaker. The caretaker is always derived from the token
 * subject (the caretaker's Keycloak id), never from a request parameter.
 */
@Facade
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationServiceFacade {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final AuthenticationProvider authenticationProvider;
    private final CaretakerService caretakerService;

    private UUID getAuthenticatedCaretakerId() {
        final UUID keycloakId = UUID.fromString(authenticationProvider.getId());
        return caretakerService.findByKeycloakId(keycloakId).getId();
    }

    public PagedModel<NotificationDTO> getPage(final Filter filter, final Pageable pageable) {
        final UUID caretakerId = getAuthenticatedCaretakerId();
        return new PagedModel<>(
                notificationService.findAll(filter.toSpecification(), pageable, caretakerId)
                        .map(notification -> notificationMapper.toDTO(
                                notification, notification.isNotificationSeen(caretakerId))));
    }

    public PagedModel<NotificationDTO> getUnreadPage(final Filter filter, final Pageable pageable) {
        final UUID caretakerId = getAuthenticatedCaretakerId();
        return new PagedModel<>(
                notificationService.findUnread(filter.toSpecification(), pageable, caretakerId)
                        .map(notification -> notificationMapper.toDTO(notification, false)));
    }

    public UnreadNotificationsDTO getUnreadNotificationCount() {
        final UUID caretakerId = getAuthenticatedCaretakerId();
        return notificationMapper.toDTO(notificationService.getUnreadNotificationCountForCaretaker(caretakerId));
    }

    @Transactional
    public void markAsRead(final UUID notificationId) {
        final UUID caretakerId = getAuthenticatedCaretakerId();
        notificationService.markNotificationAsRead(notificationMapper.toCreateCommand(caretakerId, notificationId));
    }

    @Transactional
    public void markAllAsRead() {
        final UUID caretakerId = getAuthenticatedCaretakerId();
        notificationService.markAllNotificationsAsRead(caretakerId);
    }
}
