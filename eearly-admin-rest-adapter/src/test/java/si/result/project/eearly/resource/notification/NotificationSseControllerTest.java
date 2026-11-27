package si.result.project.eearly.resource.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.sse.SseEmitterRegistry;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSseControllerTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @Mock
    private AuthenticationProvider authenticationProvider;

    @Mock
    private CaretakerService caretakerService;

    @InjectMocks
    private NotificationSseController controller;

    @Nested
    @DisplayName("Stream")
    class StreamTest {

        @Test
        @DisplayName("Should register and return emitter for authenticated caretaker")
        void stream_RegistersEmitterForAuthenticatedCaretaker() {
            UUID keycloakId = UUID.randomUUID();
            UUID caretakerId = UUID.randomUUID();
            SseEmitter emitter = mock(SseEmitter.class);

            Caretaker caretaker = mock(Caretaker.class);
            when(caretaker.getId()).thenReturn(caretakerId);

            when(authenticationProvider.getId()).thenReturn(keycloakId.toString());
            when(caretakerService.findByKeycloakId(keycloakId)).thenReturn(caretaker);
            when(sseEmitterRegistry.register(caretakerId)).thenReturn(emitter);

            SseEmitter result = controller.stream();

            assertSame(emitter, result);
            verify(authenticationProvider).getId();
            verify(caretakerService).findByKeycloakId(keycloakId);
            verify(sseEmitterRegistry).register(caretakerId);
        }

        @Test
        @DisplayName("Should propagate exception when caretaker is not found")
        void stream_PropagatesException_WhenCaretakerNotFound() {
            UUID keycloakId = UUID.randomUUID();

            when(authenticationProvider.getId()).thenReturn(keycloakId.toString());
            when(caretakerService.findByKeycloakId(keycloakId)).thenThrow(DomainException.class);

            assertThrows(DomainException.class, () -> controller.stream());
            verifyNoInteractions(sseEmitterRegistry);
        }
    }
}
