package zw.gov.mohcc.impilo.experience.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class InMemoryFetalMonitoringStreamService implements FetalMonitoringStreamService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFetalMonitoringStreamService.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String tenantId, UUID sessionId, Map<String, Object> snapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        String key = streamKey(tenantId, sessionId);
        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(key, emitter);
            emitter.complete();
        });
        emitter.onError(error -> removeEmitter(key, emitter));

        try {
            emitter.send(SseEmitter.event().name("stream.ready").data(Map.of("session_id", sessionId.toString())));
            emitter.send(SseEmitter.event().name("session.snapshot").data(snapshot));
        } catch (IOException exception) {
            removeEmitter(key, emitter);
            emitter.completeWithError(exception);
        }

        return emitter;
    }

    @Override
    public void publishSessionOpened(String tenantId, UUID sessionId, Map<String, Object> sessionPayload) {
        publish(tenantId, sessionId, "ctg.session.opened", sessionPayload);
    }

    @Override
    public void publishChunkRecorded(String tenantId, UUID sessionId, Map<String, Object> chunkPayload) {
        publish(tenantId, sessionId, "ctg.chunk.recorded", chunkPayload);
    }

    @Override
    public void publishAnnotationRecorded(String tenantId, UUID sessionId, Map<String, Object> annotationPayload) {
        publish(tenantId, sessionId, "ctg.annotation.recorded", annotationPayload);
    }

    int subscriberCount(String tenantId, UUID sessionId) {
        return emitters.getOrDefault(streamKey(tenantId, sessionId), new CopyOnWriteArrayList<>()).size();
    }

    private void publish(String tenantId, UUID sessionId, String eventName, Map<String, Object> payload) {
        String key = streamKey(tenantId, sessionId);
        List<SseEmitter> subscribers = emitters.get(key);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException exception) {
                log.warn("CTG SSE publish failed event={} sessionId={}", eventName, sessionId, exception);
                removeEmitter(key, emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> subscribers = emitters.get(key);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(emitter);
        if (subscribers.isEmpty()) {
            emitters.remove(key);
        }
    }

    private String streamKey(String tenantId, UUID sessionId) {
        return tenantId + ":" + sessionId;
    }
}
