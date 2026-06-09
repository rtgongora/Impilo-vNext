package zw.gov.mohcc.impilo.experience.scheduling;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class AppointmentCommsHistoryStore {

    private static final int MAX_HISTORY = 5000;
    private static final int MAX_MESSAGES_PER_APPOINTMENT = 200;

    private final ConcurrentLinkedDeque<Map<String, Object>> history = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Map<String, Object>>> messagesByAppointment =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> processedOutboxEvents = new ConcurrentHashMap<>();

    public void recordLifecycle(String eventType, Map<String, Object> payload, String status) {
        append("LIFECYCLE", eventType, payload, status, null, null);
    }

    public void recordNotification(String eventType, String channel, String templateKey, String status,
                                   Map<String, Object> payload) {
        append("NOTIFICATION", eventType, payload, status, channel, templateKey);
    }

    public void recordMessage(String appointmentId, String direction, String message, String senderActorId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("appointmentId", appointmentId);
        row.put("direction", direction);
        row.put("message", message);
        row.put("senderActorId", senderActorId);
        row.put("sentAt", OffsetDateTime.now().toString());
        messagesByAppointment
                .computeIfAbsent(appointmentId, ignored -> new ConcurrentLinkedDeque<>())
                .addLast(row);
        trimMessages(appointmentId);
        append("MESSAGE", direction, Map.of("appointmentId", appointmentId, "message", message), "sent", null, null);
    }

    public List<Map<String, Object>> listMessages(String appointmentId) {
        ConcurrentLinkedDeque<Map<String, Object>> deque = messagesByAppointment.get(appointmentId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }

    public List<Map<String, Object>> recentHistory(int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : history) {
            rows.add(row);
            if (rows.size() >= limit) {
                break;
            }
        }
        return rows;
    }

    public boolean markOutboxProcessed(String eventKey) {
        return processedOutboxEvents.putIfAbsent(eventKey, Boolean.TRUE) == null;
    }

    private void append(String kind, String eventType, Map<String, Object> payload, String status,
                        String channel, String templateKey) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("kind", kind);
        row.put("eventType", eventType);
        row.put("status", status);
        row.put("channel", channel);
        row.put("templateKey", templateKey);
        row.put("payload", payload != null ? payload : Map.of());
        row.put("occurredAt", OffsetDateTime.now().toString());
        history.addFirst(row);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    private void trimMessages(String appointmentId) {
        ConcurrentLinkedDeque<Map<String, Object>> deque = messagesByAppointment.get(appointmentId);
        if (deque == null) {
            return;
        }
        while (deque.size() > MAX_MESSAGES_PER_APPOINTMENT) {
            deque.removeFirst();
        }
    }
}
