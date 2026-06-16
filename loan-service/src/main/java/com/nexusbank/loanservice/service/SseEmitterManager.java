package com.nexusbank.loanservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.loanservice.dto.response.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry of active SSE connections, keyed by userId.
 *
 * A single user can have multiple active browser tabs — each tab gets its own
 * emitter stored in a per-user list. Dead emitters (completed/timed-out/errored)
 * are cleaned up lazily on the next send.
 *
 * Limitation: this state is local to one JVM instance. In a multi-instance
 * deployment the event and the subscriber may land on different instances.
 * For this single-instance academic setup that is not a concern.
 */
@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    // Keep emitters open for 1 hour; the frontend reconnects automatically.
    private static final long EMITTER_TIMEOUT_MS = 60 * 60 * 1_000L;

    private final Map<Long, List<SseEmitter>> registry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Creates and registers a new SSE connection for the given user. */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        registry.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(()   -> remove(userId, emitter));
        emitter.onError(e      -> remove(userId, emitter));

        log.debug("SSE registered for userId={}, total connections={}", userId,
                registry.getOrDefault(userId, List.of()).size());
        return emitter;
    }

    /**
     * Pushes a notification to all active connections for the given user.
     * Dead emitters encountered during the send are removed immediately.
     */
    public void send(Long userId, NotificationEvent event) {
        List<SseEmitter> emitters = registry.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active SSE connections for userId={}, event dropped: {}", userId, event.type());
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.error("Failed to serialize notification event", e);
            return;
        }

        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("notification").data(json));
                return false; // keep alive
            } catch (IOException e) {
                log.debug("SSE emitter dead for userId={}, removing", userId);
                return true;  // remove dead emitter
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = registry.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) registry.remove(userId);
        }
    }
}
