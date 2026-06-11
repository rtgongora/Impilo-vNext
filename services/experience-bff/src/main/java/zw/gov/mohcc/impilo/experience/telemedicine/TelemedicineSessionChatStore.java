package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory session chat for telemedicine / teleconsult workspaces (BFF has no JDBC for chat).
 */
@Component
public class TelemedicineSessionChatStore {

    private final List<Map<String, Object>> messages = new CopyOnWriteArrayList<>();

    public Map<String, Object> addMessage(String sessionId, String senderId, String senderName, String content, String type) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", "msg-" + UUID.randomUUID().toString().substring(0, 8));
        msg.put("sessionId", sessionId);
        msg.put("senderId", senderId);
        msg.put("senderName", senderName != null && !senderName.isBlank() ? senderName : "Unknown");
        msg.put("content", content);
        msg.put("type", type != null && !type.isBlank() ? type : "TEXT");
        msg.put("timestamp", OffsetDateTime.now().toString());
        messages.add(msg);
        return msg;
    }

    public List<Map<String, Object>> listMessages(String sessionId) {
        return messages.stream()
                .filter(m -> sessionId.equals(m.get("sessionId")))
                .collect(Collectors.toList());
    }
}
