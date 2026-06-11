package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RtcSignalingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RtcSignalingWebSocketHandler.class);

    private final RtcSignalingRoom room;
    private final ObjectMapper objectMapper;
    private final Map<String, String> sessionPeerIds = new ConcurrentHashMap<>();

    public RtcSignalingWebSocketHandler(RtcSignalingRoom room, ObjectMapper objectMapper) {
        this.room = room;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = requireRoomId(session);
        String peerId = requirePeerId(session);
        sessionPeerIds.put(session.getId(), peerId);
        room.join(roomId, peerId, session);

        ObjectNode joined = objectMapper.createObjectNode();
        joined.put("type", "peer-joined");
        joined.put("from", peerId);
        joined.put("roomId", roomId);
        joined.put("peerCount", room.peerCount(roomId));
        room.broadcastExcept(roomId, peerId, joined.toString());

        ObjectNode welcome = objectMapper.createObjectNode();
        welcome.put("type", "joined");
        welcome.put("roomId", roomId);
        welcome.put("peerId", peerId);
        welcome.put("peerCount", room.peerCount(roomId));
        welcome.set("peers", objectMapper.valueToTree(room.peerIds(roomId)));
        session.sendMessage(new TextMessage(welcome.toString()));
        log.info("RTC peer joined room={} peer={} count={}", roomId, peerId, room.peerCount(roomId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = requireRoomId(session);
        String peerId = requirePeerId(session);
        JsonNode body = objectMapper.readTree(message.getPayload());
        String type = body.path("type").asText("signal");

        if ("ping".equals(type)) {
            ObjectNode pong = objectMapper.createObjectNode();
            pong.put("type", "pong");
            session.sendMessage(new TextMessage(pong.toString()));
            return;
        }

        ObjectNode relay = objectMapper.createObjectNode();
        relay.put("type", type);
        relay.put("from", peerId);
        relay.put("roomId", roomId);
        if (body.has("payload")) {
            relay.set("payload", body.get("payload"));
        }
        room.broadcastExcept(roomId, peerId, relay.toString());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = extractRoomId(session);
        String peerId = sessionPeerIds.remove(session.getId());
        if (roomId == null || peerId == null) {
            return;
        }
        room.leave(roomId, peerId);

        ObjectNode left = objectMapper.createObjectNode();
        left.put("type", "peer-left");
        left.put("from", peerId);
        left.put("roomId", roomId);
        left.put("peerCount", room.peerCount(roomId));
        room.broadcastExcept(roomId, peerId, left.toString());
        log.info("RTC peer left room={} peer={} status={}", roomId, peerId, status);
    }

    private static String requireRoomId(WebSocketSession session) {
        String roomId = extractRoomId(session);
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Missing telemedicine session id in WebSocket path");
        }
        return roomId;
    }

    private static String requirePeerId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            throw new IllegalArgumentException("Missing WebSocket URI");
        }
        String query = uri.getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && "from".equals(kv[0]) && !kv[1].isBlank()) {
                    return kv[1];
                }
            }
        }
        throw new IllegalArgumentException("Missing from= peer id query parameter");
    }

    private static String extractRoomId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        // /ws/telemedicine/rtc/{sessionId}
        String prefix = "/ws/telemedicine/rtc/";
        if (path != null && path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return null;
    }
}
