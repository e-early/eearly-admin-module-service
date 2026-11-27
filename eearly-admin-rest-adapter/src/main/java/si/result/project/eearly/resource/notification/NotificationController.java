package si.result.project.eearly.resource.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.result.project.eearly.dto.notification.NotificationDTO;
import si.result.project.eearly.dto.notification.UnreadNotificationsDTO;
import si.result.project.eearly.facade.notification.NotificationServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Notification Controller")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationServiceFacade notificationServiceFacade;

    @Operation(description = "Get notification page")
    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<NotificationDTO>>> getPage(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Direction.DESC) final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(notificationServiceFacade.getPage(filter, pageable)).ok();
    }

    @Operation(description = "Mark notification as read")
    @PostMapping(value = "/read/{notificationId}")
    public ResponseEntity<Void> markAsRead(@PathVariable("notificationId") final UUID notificationId) {
        notificationServiceFacade.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @Operation(description = "Mark all notifications as read")
    @PostMapping(value = "/read")
    public ResponseEntity<Void> markAllAsRead() {
        notificationServiceFacade.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    @Operation(description = "Get number of unread notifications")
    @GetMapping(value = "/unread/count")
    public ResponseEntity<UnreadNotificationsDTO> getUnreadNotificationCount() {
        return ResponseEntity.ok(notificationServiceFacade.getUnreadNotificationCount());
    }

    @Operation(description = "Get unread notification page")
    @GetMapping(value = "/unread", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<NotificationDTO>>> getUnreadPage(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Direction.DESC) final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(notificationServiceFacade.getUnreadPage(filter, pageable)).ok();
    }
}
