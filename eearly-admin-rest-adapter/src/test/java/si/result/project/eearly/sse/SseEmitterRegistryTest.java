package si.result.project.eearly.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import si.result.project.eearly.mapper.notification.NotificationMapper;
import si.result.project.eearly.model.notification.Notification;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseEmitterRegistryTest {

    @Mock
    private NotificationMapper notificationMapper;

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = spy(new SseEmitterRegistry(notificationMapper));
    }

    private SseEmitter stubNextEmitter(SseEmitter emitter) {
        doReturn(emitter).when(registry).createEmitter();
        return emitter;
    }

    @Nested
    @DisplayName("Register")
    class RegisterTest {

        @Test
        @DisplayName("register returns the created emitter")
        void register_returnsEmitter() {
            stubNextEmitter(mock(SseEmitter.class));

            final SseEmitter result = registry.register(UUID.randomUUID());

            assertNotNull(result);
        }

        @Test
        @DisplayName("register adds emitter to the registry")
        void register_addsEmitterToRegistry() {
            final UUID caretakerId = UUID.randomUUID();
            stubNextEmitter(mock(SseEmitter.class));

            registry.register(caretakerId);

            assertEquals(1, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("registering twice for same caretaker replaces the first emitter")
        void register_multipleEmitters_sameCaretaker() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter firstEmitter = mock(SseEmitter.class);
            final SseEmitter secondEmitter = mock(SseEmitter.class);
            doReturn(firstEmitter, secondEmitter).when(registry).createEmitter();

            registry.register(caretakerId);
            registry.register(caretakerId);

            assertEquals(1, registry.emitterCount(caretakerId));
            verify(firstEmitter).complete();
        }

        @Test
        @DisplayName("registering for one caretaker leaves the emitters of another caretaker alone")
        void register_doesNotAffectOtherCaretakers() {
            final UUID first = UUID.randomUUID();
            final UUID second = UUID.randomUUID();
            final SseEmitter firstEmitter = mock(SseEmitter.class);
            final SseEmitter secondEmitter = mock(SseEmitter.class);
            doReturn(firstEmitter, secondEmitter).when(registry).createEmitter();

            registry.register(first);
            registry.register(second);

            assertEquals(1, registry.emitterCount(first));
            assertEquals(1, registry.emitterCount(second));
            verify(firstEmitter, never()).complete();
        }

        @Test
        @DisplayName("register attaches onError callback")
        void register_attachesErrorCallback() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));

            registry.register(caretakerId);

            verify(emitter).onError(any());
        }

        @Test
        @DisplayName("register attaches onCompletion callback")
        void register_attachesCompletionCallback() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));

            registry.register(caretakerId);

            verify(emitter).onCompletion(any(Runnable.class));
        }

        @Test
        @DisplayName("register attaches onTimeout callback")
        void register_attachesTimeoutCallback() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));

            registry.register(caretakerId);

            verify(emitter).onTimeout(any(Runnable.class));
        }

        @Test
        @DisplayName("the created emitter has no timeout of its own")
        void createEmitter_hasNoTimeout() {
            assertEquals(Long.MAX_VALUE, new SseEmitterRegistry(notificationMapper).createEmitter().getTimeout());
        }

        @Test
        @DisplayName("register sends the initial connected comment")
        void register_sendsInitialComment() throws IOException {
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));

            registry.register(UUID.randomUUID());

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    @DisplayName("Broadcast")
    class BroadcastTest {

        @Test
        @DisplayName("broadcast sends event to registered emitter")
        void broadcast_sendsToRegisteredCaretaker() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);
            clearInvocations(emitter);

            final Notification notification = stubNotification(caretakerId);
            registry.broadcast(notification);

            verify(emitter).send((SseEmitter.SseEventBuilder) any());
        }

        @Test
        @DisplayName("broadcast sends to the emitters of the addressed caretaker only")
        void broadcast_skipsOtherCaretakers() throws IOException {
            final UUID addressed = UUID.randomUUID();
            final UUID other = UUID.randomUUID();
            final SseEmitter addressedEmitter = mock(SseEmitter.class);
            final SseEmitter otherEmitter = mock(SseEmitter.class);
            doReturn(addressedEmitter, otherEmitter).when(registry).createEmitter();
            registry.register(addressed);
            registry.register(other);
            clearInvocations(addressedEmitter, otherEmitter);

            registry.broadcast(stubNotification(addressed));

            verify(addressedEmitter).send((SseEmitter.SseEventBuilder) any());
            verify(otherEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("broadcast of a notification addressed to everyone reaches every connected caretaker")
        void broadcast_nullCaretakerReachesEveryone() throws IOException {
            final SseEmitter firstEmitter = mock(SseEmitter.class);
            final SseEmitter secondEmitter = mock(SseEmitter.class);
            doReturn(firstEmitter, secondEmitter).when(registry).createEmitter();
            registry.register(UUID.randomUUID());
            registry.register(UUID.randomUUID());
            clearInvocations(firstEmitter, secondEmitter);

            registry.broadcast(stubNotification(null));

            verify(firstEmitter).send((SseEmitter.SseEventBuilder) any());
            verify(secondEmitter).send((SseEmitter.SseEventBuilder) any());
        }

        @Test
        @DisplayName("broadcast of a notification addressed to everyone does nothing when nobody is connected")
        void broadcast_nullCaretakerWithoutEmitters() {
            assertDoesNotThrow(() -> registry.broadcast(stubNotification(null)));
        }

        @Test
        @DisplayName("broadcast does nothing when caretaker has no registered emitters")
        void broadcast_skipsUnconnectedCaretaker() {
            final Notification notification = stubNotification(UUID.randomUUID());

            assertDoesNotThrow(() -> registry.broadcast(notification));
        }

        @Test
        @DisplayName("broadcast removes emitter that throws IOException")
        void broadcast_removesDeadEmitter_onIOException() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter deadEmitter = stubNextEmitter(mock(SseEmitter.class));
            doThrow(new IOException("connection closed")).when(deadEmitter).send((SseEmitter.SseEventBuilder) any());
            registry.register(caretakerId);

            registry.broadcast(stubNotification(caretakerId));

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("broadcast removes emitter that throws IllegalStateException")
        void broadcast_removesDeadEmitter_onIllegalStateException() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter deadEmitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);
            doThrow(new IllegalStateException("emitter closed")).when(deadEmitter).send((SseEmitter.SseEventBuilder) any());

            registry.broadcast(stubNotification(caretakerId));

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("broadcast to everyone removes only the dead emitters")
        void broadcast_nullCaretakerRemovesDeadEmittersOnly() throws IOException {
            final UUID deadCaretaker = UUID.randomUUID();
            final UUID aliveCaretaker = UUID.randomUUID();
            final SseEmitter deadEmitter = mock(SseEmitter.class);
            final SseEmitter aliveEmitter = mock(SseEmitter.class);
            doReturn(deadEmitter, aliveEmitter).when(registry).createEmitter();
            registry.register(deadCaretaker);
            registry.register(aliveCaretaker);
            doThrow(new IOException("dead")).when(deadEmitter).send((SseEmitter.SseEventBuilder) any());

            registry.broadcast(stubNotification(null));

            assertEquals(0, registry.emitterCount(deadCaretaker));
            assertEquals(1, registry.emitterCount(aliveCaretaker));
        }

        @Test
        @DisplayName("broadcast delivers to new emitter after re-registration replaces old one")
        void broadcast_continuesAfterDeadEmitter() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter firstEmitter = mock(SseEmitter.class);
            final SseEmitter secondEmitter = mock(SseEmitter.class);
            doReturn(firstEmitter, secondEmitter).when(registry).createEmitter();

            registry.register(caretakerId);
            registry.register(caretakerId);
            clearInvocations(firstEmitter, secondEmitter);

            registry.broadcast(stubNotification(caretakerId));

            verify(secondEmitter).send((SseEmitter.SseEventBuilder) any());
            verify(firstEmitter, never()).send((SseEmitter.SseEventBuilder) any());
        }

        @Test
        @DisplayName("broadcast removes map entry when last emitter becomes dead")
        void broadcast_removesEntryWhenListBecomesEmpty() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            doThrow(new IOException("dead")).when(emitter).send((SseEmitter.SseEventBuilder) any());
            registry.register(caretakerId);

            registry.broadcast(stubNotification(caretakerId));

            assertEquals(0, registry.emitterCount(caretakerId));
        }
    }

    @Nested
    @DisplayName("Disconnect")
    class DisconnectTest {

        @Test
        @DisplayName("disconnect calls complete on all emitters for that caretaker")
        void disconnect_completesAllEmittersForCaretaker() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter1 = mock(SseEmitter.class);
            final SseEmitter emitter2 = mock(SseEmitter.class);
            doReturn(emitter1, emitter2).when(registry).createEmitter();

            registry.register(caretakerId);
            registry.register(caretakerId);
            registry.disconnect(caretakerId);

            verify(emitter1).complete();
            verify(emitter2).complete();
        }

        @Test
        @DisplayName("disconnect removes the caretaker entry from the registry")
        void disconnect_removesEntryFromMap() {
            final UUID caretakerId = UUID.randomUUID();
            stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);

            registry.disconnect(caretakerId);

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("disconnect on unknown caretaker does not throw")
        void disconnect_noopForUnknownCaretaker() {
            assertDoesNotThrow(() -> registry.disconnect(UUID.randomUUID()));
        }

        @Test
        @DisplayName("disconnect swallows exception thrown by complete")
        void disconnect_swallowsCompleteException() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            doThrow(new RuntimeException("already closed")).when(emitter).complete();
            registry.register(caretakerId);

            assertDoesNotThrow(() -> registry.disconnect(caretakerId));
        }
    }

    @Nested
    @DisplayName("Timeout / Error / Completion callbacks")
    class RemoveEmitterTest {

        @Test
        @DisplayName("onCompletion callback removes the emitter from the registry")
        void completion_callback_removesEmitterFromRegistry() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onCompletion(captor.capture());
            captor.getValue().run();

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("onTimeout callback removes the emitter from the registry")
        void timeout_callback_removesEmitterFromRegistry() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(captor.capture());
            captor.getValue().run();

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("onError callback removes the emitter from the registry")
        void error_callback_removesEmitterFromRegistry() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<java.util.function.Consumer<Throwable>> captor =
                    ArgumentCaptor.forClass(java.util.function.Consumer.class);
            verify(emitter).onError(captor.capture());
            captor.getValue().accept(new IOException("broken pipe"));

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("onCompletion callback of the replaced emitter leaves the new emitter registered")
        void completion_callback_ofReplacedEmitter_keepsNewEmitter() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter1 = mock(SseEmitter.class);
            final SseEmitter emitter2 = mock(SseEmitter.class);
            doReturn(emitter1, emitter2).when(registry).createEmitter();

            registry.register(caretakerId);
            registry.register(caretakerId); // replaces emitter1 with emitter2

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter1).onCompletion(captor.capture());
            captor.getValue().run();

            assertEquals(1, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("onCompletion callback on active emitter removes it from the registry")
        void completion_callback_removesEmitter() {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter1 = mock(SseEmitter.class);
            final SseEmitter emitter2 = mock(SseEmitter.class);
            doReturn(emitter1, emitter2).when(registry).createEmitter();

            registry.register(caretakerId);
            registry.register(caretakerId); // replaces emitter1 with emitter2
            assertEquals(1, registry.emitterCount(caretakerId));

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter2).onCompletion(captor.capture());
            captor.getValue().run();

            assertEquals(0, registry.emitterCount(caretakerId));
        }
    }

    @Nested
    @DisplayName("Heartbeat")
    class HeartbeatTest {

        @Test
        @DisplayName("heartbeat sends ping comment to all registered emitters")
        void heartbeat_sendsPingToAllEmitters() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter emitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);
            clearInvocations(emitter);

            registry.heartbeat();

            verify(emitter).send((SseEmitter.SseEventBuilder) any());
        }

        @Test
        @DisplayName("heartbeat removes emitter that throws IOException")
        void heartbeat_removesDeadEmitter_onIOException() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter deadEmitter = stubNextEmitter(mock(SseEmitter.class));
            doThrow(new IOException("connection reset")).when(deadEmitter).send((SseEmitter.SseEventBuilder) any());
            registry.register(caretakerId);

            registry.heartbeat();

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("heartbeat removes emitter that throws IllegalStateException")
        void heartbeat_removesDeadEmitter_onIllegalStateException() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter deadEmitter = stubNextEmitter(mock(SseEmitter.class));
            registry.register(caretakerId);
            doThrow(new IllegalStateException("emitter closed")).when(deadEmitter).send((SseEmitter.SseEventBuilder) any());

            registry.heartbeat();

            assertEquals(0, registry.emitterCount(caretakerId));
        }

        @Test
        @DisplayName("heartbeat does nothing when no emitters are registered")
        void heartbeat_noopWhenEmpty() {
            assertDoesNotThrow(() -> registry.heartbeat());
        }

        @Test
        @DisplayName("heartbeat delivers to new emitter after re-registration replaces old one")
        void heartbeat_continuesAfterDeadEmitter() throws IOException {
            final UUID caretakerId = UUID.randomUUID();
            final SseEmitter firstEmitter = mock(SseEmitter.class);
            final SseEmitter aliveEmitter = mock(SseEmitter.class);
            doReturn(firstEmitter, aliveEmitter).when(registry).createEmitter();

            registry.register(caretakerId);
            registry.register(caretakerId); // replaces firstEmitter with aliveEmitter
            clearInvocations(firstEmitter, aliveEmitter);

            registry.heartbeat();

            verify(aliveEmitter).send((SseEmitter.SseEventBuilder) any());
            assertEquals(1, registry.emitterCount(caretakerId));
        }
    }

    private Notification stubNotification(UUID caretakerId) {
        final Notification notification = mock(Notification.class);
        when(notification.getCaretakerId()).thenReturn(caretakerId);
        return notification;
    }
}
