package si.result.project.eearly.mapper.notification;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import si.result.project.eearly.dto.notification.NotificationDTO;
import si.result.project.eearly.dto.notification.UnreadNotificationsDTO;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.command.CreateNotificationReadCommand;

import java.util.UUID;

@Mapper
public interface NotificationMapper {

    @Mapping(source = "notification.id", target = "id")
    @Mapping(source = "notification.type", target = "type")
    @Mapping(source = "notification.message", target = "message")
    @Mapping(source = "notification.referenceId", target = "referenceId")
    @Mapping(source = "notification.patientId", target = "patientId")
    @Mapping(source = "notification.caretakerId", target = "caretakerId")
    @Mapping(source = "isSeen", target = "isSeen")
    NotificationDTO toDTO(Notification notification, boolean isSeen);

    UnreadNotificationsDTO toDTO(Long count);

    CreateNotificationReadCommand toCreateCommand(UUID caretakerId, UUID notificationId);
}
