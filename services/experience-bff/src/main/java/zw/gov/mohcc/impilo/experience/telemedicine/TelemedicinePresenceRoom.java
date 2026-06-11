package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps user / patient / CPID keys to open presence WebSocket sessions for incoming call delivery.
 */
@Component
public final class TelemedicinePresenceRoom {

    private final Map<String, Map<String, WebSocketSession>> sessionsByKey = new ConcurrentHashMap<>();

    public void register(String key, String connectionId, WebSocketSession session) {
        if (key == null || key.isBlank()) {
            return;
        }
        sessionsByKey
                .computeIfAbsent(key, __ -> new ConcurrentHashMap<>())
                .put(connectionId, session);
    }

    public void unregister(String key, String connectionId) {
        if (key == null || key.isBlank()) {
            return;
        }
        Map<String, WebSocketSession> bucket = sessionsByKey.get(key);
        if (bucket == null) {
            return;
        }
        bucket.remove(connectionId);
        if (bucket.isEmpty()) {
            sessionsByKey.remove(key);
        }
    }

    public void unregisterAll(String connectionId, Set<String> keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            unregister(key, connectionId);
        }
    }

    public void notifyKeys(Iterable<String> keys, String payload) throws IOException {
        TextMessage message = new TextMessage(payload);
        for (String key : keys) {
            Map<String, WebSocketSession> bucket = sessionsByKey.get(key);
            if (bucket == null) {
                continue;
            }
            for (WebSocketSession session : bucket.values()) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            }
        }
    }
}
