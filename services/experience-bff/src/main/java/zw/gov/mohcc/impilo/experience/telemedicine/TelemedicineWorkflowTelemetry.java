package zw.gov.mohcc.impilo.experience.telemedicine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Ephemeral operational telemetry for the telemedicine comms workflow.
 *
 * <p>This component holds only process-local observability counters (queue depth, failure tallies,
 * delivery-receipt lag) used to render the operational console snapshot. It is intentionally
 * <strong>not</strong> a store: the durable workflow event history is owned by the TSHEPO audit
 * sovereign — every recording method here dual-ingests an immutable audit event via
 * {@link TshepoAuditServiceClient}. The experience BFF holds no datasource of its own, so these
 * counters reset on restart by design and are not a source of record.</p>
 */
@Component
public class TelemedicineWorkflowTelemetry {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineWorkflowTelemetry.class);
    private static final String EVENT_TYPE = "TELEMEDICINE_WORKFLOW";

    private final TshepoAuditServiceClient auditClient;

    private final AtomicLong inFlightEvents = new AtomicLong(0);
    private final LongAdder deadLetterEvents = new LongAdder();
    private final LongAdder webhookFailures = new LongAdder();
    private final LongAdder notificationFailures = new LongAdder();
    private final LongAdder notificationDispatches = new LongAdder();
    private final LongAdder stuckWaitingRoomNotifications = new LongAdder();
    private final LongAdder supportEscalationFailures = new LongAdder();
    private final LongAdder deliveryReceiptLagMsSum = new LongAdder();
    private final LongAdder deliveryReceiptLagMsCount = new LongAdder();

    public TelemedicineWorkflowTelemetry(TshepoAuditServiceClient auditClient) {
        this.auditClient = auditClient;
    }

    public void onEventReceived(String eventType, Map<String, Object> payload) {
        inFlightEvents.incrementAndGet();
        ingest("RECEIVED", eventType, payload, "accepted", null, null, null);
    }

    public void onEventProcessed(String eventType, Map<String, Object> payload) {
        inFlightEvents.updateAndGet(v -> Math.max(0, v - 1));
        ingest("PROCESSED", eventType, payload, "processed", null, null, null);
    }

    public void onEventFailed(String eventType, Map<String, Object> payload, String reason) {
        inFlightEvents.updateAndGet(v -> Math.max(0, v - 1));
        deadLetterEvents.increment();
        ingest("FAILED", eventType, payload, reason, null, null, null);
    }

    public void recordNotificationDispatch(String eventType, String channel, String templateKey, String status, Map<String, Object> payload) {
        notificationDispatches.increment();
        if ("FAILED".equalsIgnoreCase(status)) {
            notificationFailures.increment();
        }
        ingest("NOTIFICATION", eventType, payload, status, channel, templateKey, null);
    }

    public void recordWebhookFailure(String eventType, Map<String, Object> payload, String reason) {
        webhookFailures.increment();
        ingest("WEBHOOK_FAILURE", eventType, payload, reason, null, null, null);
    }

    public void recordStuckWaitingRoomNotification(String eventType, Map<String, Object> payload) {
        stuckWaitingRoomNotifications.increment();
        ingest("WAITING_ROOM_STUCK", eventType, payload, "stuck_waiting_room_notification", null, null, null);
    }

    public void recordSupportEscalationFailure(String eventType, Map<String, Object> payload, String reason) {
        supportEscalationFailures.increment();
        ingest("SUPPORT_ESCALATION_FAILURE", eventType, payload, reason, null, null, null);
    }

    public void recordDeliveryReceiptLagMs(long lagMs, String eventType, Map<String, Object> payload) {
        if (lagMs < 0) {
            return;
        }
        deliveryReceiptLagMsSum.add(lagMs);
        deliveryReceiptLagMsCount.increment();
        ingest("DELIVERY_RECEIPT", eventType, payload, "lag_recorded", null, null, lagMs);
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

    /**
     * Workflow event history is retained by the TSHEPO audit sovereign; the BFF no longer keeps a
     * local projection, so this is informational only.
     */
    public Map<String, Object> retentionConfig() {
        return Map.of(
                "retained_by", "tshepo-audit",
                "note", "Telemedicine workflow history is owned by the TSHEPO audit service; "
                        + "retention is governed there, not in the experience BFF."
        );
    }

    /**
     * Informational — the BFF holds no local history to reconfigure. Retention is owned by tshepo-audit.
     */
    public Map<String, Object> updateRetention(Integer maxHistory, Integer retentionHours) {
        return retentionConfig();
    }

    /**
     * Informational — there is no local history deque to prune. Always returns zero removed.
     */
    public int pruneExpired() {
        return 0;
    }

    private void ingest(
            String action,
            String eventType,
            Map<String, Object> payload,
            String outcome,
            String channel,
            String templateKey,
            Long deliveryLagMs) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("action", action);
            detail.put("workflow_event", eventType);
            detail.put("outcome", outcome);
            detail.put("channel", channel);
            detail.put("template_key", templateKey);
            detail.put("delivery_receipt_lag_ms", deliveryLagMs);
            detail.put("links", linkage(payload));

            String sessionId = string(payload, "id", "sessionId", "session_id");
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", EVENT_TYPE);
            event.put("actorId", "experience-bff");
            event.put("actorType", "SERVICE");
            event.put("subjectRef", "telemedicine-session:" + (sessionId != null ? sessionId : "unknown"));
            event.put("resourceType", "TELEMEDICINE_WORKFLOW");
            event.put("action", action);
            event.put("outcome", outcome != null ? outcome : "SUCCESS");
            event.put("purposeOfUse", "OPERATIONS");
            event.put("occurredAt", OffsetDateTime.now().toString());
            event.put("detail", detail);
            auditClient.ingestAuditEvent(event);
        } catch (Exception ex) {
            log.debug("Telemedicine workflow audit ingest failed for {}: {}", eventType, ex.getMessage());
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
}
