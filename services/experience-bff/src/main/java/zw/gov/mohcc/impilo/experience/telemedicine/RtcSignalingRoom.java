package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory WebRTC signaling room keyed by telemedicine session id.
 * Relays SDP offers/answers and ICE candidates between peers in the same room.
 */
@Component
public final class RtcSignalingRoom {

    private final Map<String, Map<String, WebSocketSession>> peersByRoom = new ConcurrentHashMap<>();

    public void join(String roomId, String peerId, WebSocketSession session) {
        peersByRoom
                .computeIfAbsent(roomId, __ -> new ConcurrentHashMap<>())
                .put(peerId, session);
    }

    public void leave(String roomId, String peerId) {
        Map<String, WebSocketSession> room = peersByRoom.get(roomId);
        if (room == null) {
            return;
        }
        room.remove(peerId);
        if (room.isEmpty()) {
            peersByRoom.remove(roomId);
        }
    }

    public int peerCount(String roomId) {
        Map<String, WebSocketSession> room = peersByRoom.get(roomId);
        return room == null ? 0 : room.size();
    }

    public void broadcastExcept(String roomId, String fromPeerId, String payload) throws IOException {
        Map<String, WebSocketSession> room = peersByRoom.get(roomId);
        if (room == null) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (Map.Entry<String, WebSocketSession> entry : room.entrySet()) {
            if (entry.getKey().equals(fromPeerId)) {
                continue;
            }
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(message);
                }
            }
        }
    }

    public Set<String> peerIds(String roomId) {
        Map<String, WebSocketSession> room = peersByRoom.get(roomId);
        return room == null ? Set.of() : Set.copyOf(room.keySet());
    }
}
