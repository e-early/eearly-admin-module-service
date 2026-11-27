package si.result.project.eearly.resource.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.sse.SseEmitterRegistry;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;

/**
 * Real-time notification stream of the authenticated caretaker. The caretaker is always derived from the token
 * subject (the caretaker's Keycloak id), never from a request parameter. The token is sent in the
 * {@code Authorization} header like on every other endpoint.
 */
@Tag(name = "Notification Controller")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSseController {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final AuthenticationProvider authenticationProvider;
    private final CaretakerService caretakerService;

    @Operation(description = "Open SSE stream for real-time notifications")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        UUID keycloakId = UUID.fromString(authenticationProvider.getId());
        return sseEmitterRegistry.register(caretakerService.findByKeycloakId(keycloakId).getId());
    }
}
