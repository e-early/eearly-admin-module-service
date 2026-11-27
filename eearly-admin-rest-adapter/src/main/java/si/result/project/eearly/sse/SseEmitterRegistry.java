package si.result.project.eearly.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import si.result.project.eearly.dto.notification.NotificationDTO;
import si.result.project.eearly.mapper.notification.NotificationMapper;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.port.notification.SseNotificationPublisher;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the open notification streams and pushes notifications to them. It replaces the no-op default
 * {@link SseNotificationPublisher}.
 *
 * <p>Delivery: a notification addressed to a caretaker goes only to the streams of that caretaker; a notification
 * addressed to no caretaker is meant for everyone and goes to every connected caretaker. The payload is the same
 * {@link NotificationDTO} the REST API returns (ids, type and message code, no personal data).
 *
 * <p>Limits: a caretaker has at most one open stream (a new one replaces and completes the previous one), a stream
 * is removed when it completes, fails or times out, and a heartbeat every {@value #HEARTBEAT_DELAY_MS} ms drops
 * streams whose client has gone. The stream itself has no timeout (the client reconnects when it wants to).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry implements SseNotificationPublisher {

    static final long HEARTBEAT_DELAY_MS = 30_000;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters
            = new ConcurrentHashMap<>();

    private final NotificationMapper notificationMapper;

    public SseEmitter register(UUID caretakerId) {
        final SseEmitter emitter = createEmitter();
        emitter.onError(e -> removeEmitter(caretakerId, emitter));
        emitter.onTimeout(() -> removeEmitter(caretakerId, emitter));
        emitter.onCompletion(() -> removeEmitter(caretakerId, emitter));
        final CopyOnWriteArrayList<SseEmitter> newList = new CopyOnWriteArrayList<>();
        newList.add(emitter);
        final List<SseEmitter> old = emitters.put(caretakerId, newList);
        if (old != null) {
            old.forEach(e -> completeSafely(e, caretakerId));
        }
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.debug("Failed to send initial SSE ping for caretaker {}: {}", caretakerId, e.getMessage());
        }
        log.debug("Registered SSE emitter for caretaker {}", caretakerId);
        return emitter;
    }

    public void disconnect(UUID caretakerId) {
        final List<SseEmitter> list = emitters.remove(caretakerId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            completeSafely(emitter, caretakerId);
        }
        log.debug("Disconnected all SSE emitters for caretaker {}", caretakerId);
    }

    @Override
    public void broadcast(Notification notification) {
        final UUID caretakerId = notification.getCaretakerId();
        final NotificationDTO payload = toPayload(notification);
        if (caretakerId == null) {
            emitters.forEach((connectedCaretakerId, list) -> send(connectedCaretakerId, list, payload));
            return;
        }
        final List<SseEmitter> caretakerEmitters = emitters.get(caretakerId);
        if (caretakerEmitters == null || caretakerEmitters.isEmpty()) {
            log.debug("SSE broadcast skipped: no active emitters for caretaker {}", caretakerId);
            return;
        }
        send(caretakerId, caretakerEmitters, payload);
    }

    @Scheduled(fixedDelay = HEARTBEAT_DELAY_MS)
    public void heartbeat() {
        emitters.forEach((caretakerId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    log.debug("Removing dead SSE emitter for caretaker {} during heartbeat: {}", caretakerId, e.getMessage());
                    removeEmitter(caretakerId, emitter);
                }
            }
        });
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(Long.MAX_VALUE);
    }

    private void send(UUID caretakerId, List<SseEmitter> caretakerEmitters, NotificationDTO payload) {
        for (SseEmitter emitter : caretakerEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("Removing dead SSE emitter for caretaker {} during broadcast: {}", caretakerId, e.getMessage());
                removeEmitter(caretakerId, emitter);
            }
        }
    }

    private void completeSafely(final SseEmitter emitter, final UUID caretakerId) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("Failed to complete SSE emitter for caretaker {}: {}", caretakerId, ex.getMessage());
        }
    }

    private void removeEmitter(UUID caretakerId, SseEmitter emitter) {
        final List<SseEmitter> list = emitters.get(caretakerId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(caretakerId, list);
            }
        }
        log.debug("Removed SSE emitter for caretaker {}", caretakerId);
    }

    private NotificationDTO toPayload(Notification notification) {
        return notificationMapper.toDTO(notification, false);
    }

    int emitterCount(UUID caretakerId) {
        final List<SseEmitter> list = emitters.get(caretakerId);
        return list == null ? 0 : list.size();
    }
}
