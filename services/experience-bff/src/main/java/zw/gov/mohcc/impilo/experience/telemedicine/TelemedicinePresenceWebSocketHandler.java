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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelemedicinePresenceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemedicinePresenceWebSocketHandler.class);

    private final TelemedicinePresenceRoom presenceRoom;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<String>> connectionKeys = new ConcurrentHashMap<>();

    public TelemedicinePresenceWebSocketHandler(TelemedicinePresenceRoom presenceRoom, ObjectMapper objectMapper) {
        this.presenceRoom = presenceRoom;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = requireUserId(session);
        String connectionId = session.getId();
        Set<String> keys = new HashSet<>();
        keys.add("user:" + userId);
        connectionKeys.put(connectionId, keys);
        presenceRoom.register("user:" + userId, connectionId, session);

        ObjectNode welcome = objectMapper.createObjectNode();
        welcome.put("type", "presence-ready");
        welcome.put("userId", userId);
        session.sendMessage(new TextMessage(welcome.toString()));
        log.debug("Telemedicine presence connected user={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode body = objectMapper.readTree(message.getPayload());
        String type = body.path("type").asText("");

        if ("ping".equals(type)) {
            ObjectNode pong = objectMapper.createObjectNode();
            pong.put("type", "pong");
            session.sendMessage(new TextMessage(pong.toString()));
            return;
        }

        if ("register".equals(type)) {
            String connectionId = session.getId();
            Set<String> keys = connectionKeys.computeIfAbsent(connectionId, __ -> new HashSet<>());
            JsonNode payload = body.path("payload");
            String patientId = text(payload, "patientId");
            String cpid = text(payload, "cpid");
            if (!patientId.isBlank()) {
                keys.add("patient:" + patientId);
                presenceRoom.register("patient:" + patientId, connectionId, session);
            }
            if (!cpid.isBlank()) {
                keys.add("cpid:" + cpid);
                presenceRoom.register("cpid:" + cpid, connectionId, session);
            }
            ObjectNode ack = objectMapper.createObjectNode();
            ack.put("type", "registered");
            ack.put("patientId", patientId);
            ack.put("cpid", cpid);
            session.sendMessage(new TextMessage(ack.toString()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String connectionId = session.getId();
        Set<String> keys = connectionKeys.remove(connectionId);
        if (keys != null) {
            presenceRoom.unregisterAll(connectionId, keys);
        }
        log.debug("Telemedicine presence disconnected status={}", status);
    }

    private static String requireUserId(WebSocketSession session) {
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
        throw new IllegalArgumentException("Missing from= user id query parameter");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
