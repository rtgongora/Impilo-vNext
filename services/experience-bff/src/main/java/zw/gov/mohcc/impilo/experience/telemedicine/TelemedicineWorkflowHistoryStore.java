package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class TelemedicineWorkflowHistoryStore {

    private volatile int maxHistory = 2000;
    private volatile int retentionHours = 24 * 7;

    private final ConcurrentLinkedDeque<Map<String, Object>> history = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Map<String, Object>> latestStateBySession = new ConcurrentHashMap<>();
    private final AtomicLong inFlightEvents = new AtomicLong(0);
    private final LongAdder deadLetterEvents = new LongAdder();
    private final LongAdder webhookFailures = new LongAdder();
    private final LongAdder notificationFailures = new LongAdder();
    private final LongAdder notificationDispatches = new LongAdder();
    private final LongAdder stuckWaitingRoomNotifications = new LongAdder();
    private final LongAdder supportEscalationFailures = new LongAdder();
    private final LongAdder deliveryReceiptLagMsSum = new LongAdder();
    private final LongAdder deliveryReceiptLagMsCount = new LongAdder();

    public void onEventReceived(String eventType, Map<String, Object> payload) {
        inFlightEvents.incrementAndGet();
        append("RECEIVED", eventType, payload, "accepted", null, null, null, null);
    }

    public void onEventProcessed(String eventType, Map<String, Object> payload) {
        inFlightEvents.updateAndGet(v -> Math.max(0, v - 1));
        append("PROCESSED", eventType, payload, "processed", null, null, null, null);
    }

    public void onEventFailed(String eventType, Map<String, Object> payload, String reason) {
        inFlightEvents.updateAndGet(v -> Math.max(0, v - 1));
        deadLetterEvents.increment();
        append("FAILED", eventType, payload, reason, null, null, null, null);
    }

    public void recordNotificationDispatch(String eventType, String channel, String templateKey, String status, Map<String, Object> payload) {
        notificationDispatches.increment();
        if ("FAILED".equalsIgnoreCase(status)) {
            notificationFailures.increment();
        }
        append("NOTIFICATION", eventType, payload, status, channel, templateKey, null, null);
    }

    public void recordWebhookFailure(String eventType, Map<String, Object> payload, String reason) {
        webhookFailures.increment();
        append("WEBHOOK_FAILURE", eventType, payload, reason, null, null, null, null);
    }

    public void recordStuckWaitingRoomNotification(String eventType, Map<String, Object> payload) {
        stuckWaitingRoomNotifications.increment();
        append("WAITING_ROOM_STUCK", eventType, payload, "stuck_waiting_room_notification", null, null, null, null);
    }

    public void recordSupportEscalationFailure(String eventType, Map<String, Object> payload, String reason) {
        supportEscalationFailures.increment();
        append("SUPPORT_ESCALATION_FAILURE", eventType, payload, reason, null, null, null, null);
    }

    public void recordDeliveryReceiptLagMs(long lagMs, String eventType, Map<String, Object> payload) {
        if (lagMs < 0) {
            return;
        }
        deliveryReceiptLagMsSum.add(lagMs);
        deliveryReceiptLagMsCount.increment();
        append("DELIVERY_RECEIPT", eventType, payload, "lag_recorded", null, null, lagMs, null);
    }

    public Map<String, Object> operationalSnapshot(Map<String, Object> telemedicineMetrics) {
        long lagCount = deliveryReceiptLagMsCount.sum();
        double avgLag = lagCount == 0 ? 0D : (double) deliveryReceiptLagMsSum.sum() / (double) lagCount;
        long queueDepth = inFlightEvents.get();
        long deadLetters = deadLetterEvents.sum();
        String queueStatus = deadLetters > 0 || queueDepth > 25 ? "DEGRADED" : "ACTIVE";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reminder_queue_status", queueStatus);
        out.put("queue_depth", queueDepth);
        out.put("join_link_notification_failures", notificationFailures.sum());
        out.put("push_sms_whatsapp_fallback_rates", telemedicineMetrics.getOrDefault("video_audio_fallback_events", 0L));
        out.put("webhook_failures_between_telemedicine_and_comms_hub", webhookFailures.sum());
        out.put("delivery_receipt_lag_ms", avgLag);
        out.put("failed_telemedicine_support_escalations", supportEscalationFailures.sum());
        out.put("stuck_waiting_room_notifications", stuckWaitingRoomNotifications.sum());
        out.put("dead_letter_events", deadLetters);
        out.put("last_refreshed_at", OffsetDateTime.now().toString());
        return out;
    }

    public List<Map<String, Object>> history(
            String sessionId,
            int limit,
            String eventType,
            String action,
            OffsetDateTime from,
            OffsetDateTime to) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> all = new ArrayList<>(history);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Map<String, Object> item = all.get(i);
            if (!matchesSession(item, sessionId)) continue;
            if (!matchesEventType(item, eventType)) continue;
            if (!matchesAction(item, action)) continue;
            if (!matchesTimeWindow(item, from, to)) continue;
            filtered.add(item);
            if (filtered.size() >= boundedLimit) {
                break;
            }
        }
        return filtered;
    }

    public Map<String, Object> retentionConfig() {
        return Map.of(
                "max_history", maxHistory,
                "retention_hours", retentionHours
        );
    }

    public Map<String, Object> updateRetention(Integer maxHistory, Integer retentionHours) {
        if (maxHistory != null && maxHistory > 0) {
            this.maxHistory = Math.min(maxHistory, 100000);
        }
        if (retentionHours != null && retentionHours > 0) {
            this.retentionHours = Math.min(retentionHours, 24 * 365);
        }
        return retentionConfig();
    }

    public int pruneExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(retentionHours);
        int removed = 0;
        while (true) {
            Map<String, Object> first = history.peekFirst();
            if (first == null) break;
            OffsetDateTime occurred = parseDate(str(first.get("occurred_at")));
            if (occurred == null || occurred.isBefore(cutoff)) {
                history.pollFirst();
                removed++;
                continue;
            }
            break;
        }
        return removed;
    }

    private void append(
            String action,
            String eventType,
            Map<String, Object> payload,
            String outcome,
            String channel,
            String templateKey,
            Long deliveryLagMs,
            String notes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("action", action);
        row.put("event_type", eventType);
        row.put("session_id", string(payload, "id", "sessionId", "session_id"));
        row.put("tenant_id", string(payload, "tenantId", "tenant_id"));
        row.put("outcome", outcome);
        row.put("channel", channel);
        row.put("template_key", templateKey);
        row.put("delivery_receipt_lag_ms", deliveryLagMs);
        row.put("notes", notes);
        row.put("occurred_at", OffsetDateTime.now().toString());
        row.put("links", linkage(payload));
        history.addLast(row);
        while (history.size() > maxHistory) {
            history.pollFirst();
        }
        pruneExpired();
        String sessionId = str(row.get("session_id"));
        if (sessionId != null && !sessionId.isBlank()) {
            latestStateBySession.put(sessionId, row);
        }
    }

    private Map<String, Object> linkage(Map<String, Object> payload) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("client", string(payload, "patientCpid", "patientId", "patient_id"));
        links.put("provider", string(payload, "providerId", "provider_id"));
        links.put("facility", string(payload, "facilityId", "facility_id"));
        links.put("session", string(payload, "id", "sessionId", "session_id"));
        links.put("appointment", string(payload, "appointmentId", "appointment_id"));
        links.put("referral", string(payload, "referralId", "referral_id"));
        links.put("prescription", string(payload, "prescriptionId", "prescription_id"));
        links.put("lab_request", string(payload, "labRequestId", "lab_request_id"));
        links.put("payment_or_claim", string(payload, "paymentId", "claimId", "billingId"));
        links.put("helpdesk_ticket", string(payload, "ticketId", "supportTicketId"));
        links.put("followup_task", string(payload, "followupTaskId", "taskId"));
        links.values().removeIf(v -> v == null || (v instanceof String s && s.isBlank()));
        return links;
    }

    private String string(Map<String, Object> payload, String... keys) {
        if (payload == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean matchesSession(Map<String, Object> row, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return true;
        return sessionId.equals(str(row.get("session_id")));
    }

    private boolean matchesEventType(Map<String, Object> row, String eventType) {
        if (eventType == null || eventType.isBlank()) return true;
        String rowValue = str(row.get("event_type"));
        return rowValue != null && rowValue.toLowerCase().contains(eventType.toLowerCase());
    }

    private boolean matchesAction(Map<String, Object> row, String action) {
        if (action == null || action.isBlank()) return true;
        String rowValue = str(row.get("action"));
        return rowValue != null && rowValue.equalsIgnoreCase(action);
    }

    private boolean matchesTimeWindow(Map<String, Object> row, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) return true;
        OffsetDateTime occurred = parseDate(str(row.get("occurred_at")));
        if (occurred == null) return false;
        if (from != null && occurred.isBefore(from)) return false;
        if (to != null && occurred.isAfter(to)) return false;
        return true;
    }

    private OffsetDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}
