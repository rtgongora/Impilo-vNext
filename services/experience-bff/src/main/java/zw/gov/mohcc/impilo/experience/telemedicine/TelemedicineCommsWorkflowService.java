package zw.gov.mohcc.impilo.experience.telemedicine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.events.TelemedicineCommunicationEventPublisher;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TelemedicineCommsWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineCommsWorkflowService.class);
    private static final Set<String> SENSITIVE_TERMS = Set.of(
            "diagnosis", "hiv", "pregnancy", "medication", "mental", "test result", "viral load");
    private static final Set<String> INSECURE_CHANNELS = Set.of("SMS", "WHATSAPP", "EMAIL");
    private static final String SAFE_DEFAULT_MESSAGE = "You have an update from your provider. Please open Impilo or contact your facility for details.";

    private final NotificationServiceClient notificationClient;
    private final SupportServiceClient supportClient;
    private final TelemedicineCommunicationEventPublisher eventPublisher;
    private final TelemedicineCommsMetricsStore metricsStore;
    private final TelemedicineWorkflowHistoryStore workflowHistoryStore;

    public TelemedicineCommsWorkflowService(
            NotificationServiceClient notificationClient,
            SupportServiceClient supportClient,
            TelemedicineCommunicationEventPublisher eventPublisher,
            TelemedicineCommsMetricsStore metricsStore,
            TelemedicineWorkflowHistoryStore workflowHistoryStore) {
        this.notificationClient = notificationClient;
        this.supportClient = supportClient;
        this.eventPublisher = eventPublisher;
        this.metricsStore = metricsStore;
        this.workflowHistoryStore = workflowHistoryStore;
    }

    public void processLifecycleEvent(String eventType, Map<String, Object> payload) {
        if (eventType == null || eventType.isBlank()) {
            return;
        }
        workflowHistoryStore.onEventReceived(eventType, payload);
        try {
            switch (eventType) {
                case "telemedicine.session.scheduled", "telemedicine.session.rescheduled" -> {
                    sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_REMINDER", payload,
                            "Your virtual consultation is ready. Please open Impilo to join.");
                    metricsStore.incrementRemindersSent();
                    emitCommsEvent("telemedicine.communication.reminder_sent", payload);
                }
                case "telemedicine.session.join_link.created" -> {
                    sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_JOIN_LINK", payload,
                            "Your virtual consultation is ready. Please open Impilo to join.");
                    metricsStore.incrementJoinInstructionsSent();
                    recordJoinReceiptLag(eventType, payload);
                }
                case "telemedicine.session.started" -> metricsStore.incrementJoinSuccessEvents();
                case "telemedicine.session.provider_late" -> {
                    sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_PROVIDER_LATE", payload,
                            "Your provider is running late. Please stay in the waiting room.");
                    metricsStore.incrementProviderDelayNotifications();
                }
                case "telemedicine.session.waiting_room.entered" -> {
                    if (isStuckWaitingRoom(payload)) {
                        workflowHistoryStore.recordStuckWaitingRoomNotification(eventType, payload);
                    }
                }
                case "telemedicine.session.client_not_joined" -> handleNoShowHeuristics(eventType, payload);
                case "telemedicine.session.video_failed" -> {
                    sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_VIDEO_FALLBACK", payload,
                            "Video connection failed. You can continue by audio in Impilo.");
                    sendSafeNotification(eventType, "SMS", "TELEMEDICINE_VIDEO_FALLBACK", payload,
                            "Video connection failed. Open Impilo to continue by audio.");
                    metricsStore.incrementFallbackEvents();
                }
                case "telemedicine.session.audio_failed" -> {
                    sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_AUDIO_FALLBACK", payload,
                            "Audio connection failed. Open Impilo for secure chat or support.");
                    escalateToSupport(eventType, payload, "Audio fallback requested");
                    metricsStore.incrementFallbackEvents();
                    metricsStore.incrementJoinSupportRequests();
                    emitCommsEvent("telemedicine.communication.join_help_requested", payload);
                }
                case "telemedicine.session.support_requested" -> {
                    escalateToSupport(eventType, payload, "Client requested telemedicine join support");
                    metricsStore.incrementJoinSupportRequests();
                    emitCommsEvent("telemedicine.communication.join_help_requested", payload);
                }
                case "telemedicine.session.completed", "telemedicine.session.followup_required",
                     "telemedicine.session.prescription_created", "telemedicine.session.referral_created",
                     "telemedicine.session.lab_request_created", "telemedicine.session.payment_required",
                     "telemedicine.session.claim_initiated" -> {
                    sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_FOLLOWUP", payload,
                            "Your follow-up has been scheduled. Please open Impilo to view details.");
                    metricsStore.incrementFollowupsSent();
                    emitCommsEvent("telemedicine.communication.followup_sent", payload);
                }
                default -> log.debug("No comms workflow bound to event {}", eventType);
            }
            workflowHistoryStore.onEventProcessed(eventType, payload);
        } catch (Exception ex) {
            workflowHistoryStore.onEventFailed(eventType, payload, ex.getMessage());
            throw new IllegalStateException("Failed telemedicine comms workflow for " + eventType, ex);
        }
    }

    private void emitCommsEvent(String eventType, Map<String, Object> payload) {
        eventPublisher.publish(eventType, string(payload, "id", "sessionId", "referralId"), linkagePayload(payload));
    }

    private void sendSafeNotification(String eventType, String channel, String templateKey, Map<String, Object> payload, String candidateMessage) {
        String recipient = string(payload, "patientCpid", "patientId", "patient_id");
        if (recipient == null || recipient.isBlank()) {
            metricsStore.incrementClientUnreachable();
            emitCommsEvent("telemedicine.communication.client_unreachable", payload);
            workflowHistoryStore.recordNotificationDispatch(eventType, channel, templateKey, "FAILED_UNREACHABLE", payload);
            return;
        }
        String safeMessage = enforceSensitiveContentPolicy(channel, candidateMessage);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("templateKey", templateKey);
            body.put("channel", channel);
            body.put("to", recipient);
            body.put("variables", Map.of(
                    "message", safeMessage,
                    "telemedicineLinkage", linkagePayload(payload)
            ));
            notificationClient.sendNotification(body);
            workflowHistoryStore.recordNotificationDispatch(eventType, channel, templateKey, "SENT", payload);
        } catch (Exception ex) {
            log.warn("Telemedicine notification failed on {}: {}", channel, ex.getMessage());
            workflowHistoryStore.recordNotificationDispatch(eventType, channel, templateKey, "FAILED", payload);
            if (!"SMS".equalsIgnoreCase(channel)) {
                try {
                    Map<String, Object> fallback = new LinkedHashMap<>();
                    fallback.put("templateKey", templateKey + "_FALLBACK_SMS");
                    fallback.put("channel", "SMS");
                    fallback.put("to", recipient);
                    fallback.put("variables", Map.of(
                            "message", enforceSensitiveContentPolicy("SMS", candidateMessage),
                            "telemedicineLinkage", linkagePayload(payload)
                    ));
                    notificationClient.sendNotification(fallback);
                    metricsStore.incrementFallbackEvents();
                    workflowHistoryStore.recordNotificationDispatch(eventType, "SMS", templateKey + "_FALLBACK_SMS", "SENT_FALLBACK", payload);
                } catch (Exception ignored) {
                    metricsStore.incrementClientUnreachable();
                    emitCommsEvent("telemedicine.communication.client_unreachable", payload);
                    workflowHistoryStore.recordNotificationDispatch(eventType, "SMS", templateKey + "_FALLBACK_SMS", "FAILED", payload);
                }
            }
        }
    }

    private void escalateToSupport(String eventType, Map<String, Object> payload, String reason) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("title", "Telemedicine support");
            ticket.put("description", reason);
            ticket.put("reporterRef", string(payload, "patientCpid", "patientId", "patient_id", "providerId"));
            ticket.put("category", "TELEMEDICINE_SUPPORT");
            ticket.put("priority", "HIGH");
            ticket.put("facilityRef", string(payload, "facilityId", "facility_id"));
            ticket.put("metadata", linkagePayload(payload));
            supportClient.createTicket(ticket);
            metricsStore.incrementEscalationToAgent();
            emitCommsEvent("telemedicine.communication.escalated_to_agent", payload);
        } catch (Exception ex) {
            log.warn("Telemedicine support escalation failed: {}", ex.getMessage());
            workflowHistoryStore.recordSupportEscalationFailure(eventType, payload, ex.getMessage());
        } finally {
            metricsStore.recordSupportResponseTimeMs(System.currentTimeMillis() - startMs);
        }
    }

    private void handleNoShowHeuristics(String eventType, Map<String, Object> payload) {
        if (hasJoinOpenedSignal(payload)) {
            workflowHistoryStore.recordNotificationDispatch(eventType, "IN_APP", "TELEMEDICINE_NO_SHOW_NUDGE", "SUPPRESSED_ALREADY_OPENED", payload);
            return;
        }
        if (hasDeliveryFailure(payload)) {
            sendSafeNotification(eventType, "SMS", "TELEMEDICINE_JOIN_RECOVERY", payload,
                    "Your virtual consultation is ready. Please open Impilo or contact your facility for support.");
            metricsStore.incrementJoinSupportRequests();
            emitCommsEvent("telemedicine.communication.client_unreachable", payload);
            return;
        }
        if (isTooEarlyForNoShow(payload)) {
            workflowHistoryStore.recordNotificationDispatch(eventType, "IN_APP", "TELEMEDICINE_NO_SHOW_NUDGE", "SUPPRESSED_TOO_EARLY", payload);
            return;
        }
        sendSafeNotification(eventType, "IN_APP", "TELEMEDICINE_NO_SHOW_NUDGE", payload,
                "Your consultation is ready. Please open Impilo to join now.");
        sendSafeNotification(eventType, "SMS", "TELEMEDICINE_NO_SHOW_NUDGE", payload,
                "Your consultation is ready. Please open Impilo to join now.");
        metricsStore.incrementNoShowNudgesSent();
        emitCommsEvent("telemedicine.communication.no_show_nudge_sent", payload);
    }

    private void recordJoinReceiptLag(String eventType, Map<String, Object> payload) {
        OffsetDateTime createdAt = parseDate(string(payload, "createdAt", "created_at"));
        OffsetDateTime deliveredAt = parseDate(string(payload, "notificationDeliveredAt", "deliveredAt", "delivery_receipt_at"));
        if (createdAt != null && deliveredAt != null && deliveredAt.isAfter(createdAt)) {
            long lagMs = Duration.between(createdAt, deliveredAt).toMillis();
            workflowHistoryStore.recordDeliveryReceiptLagMs(lagMs, eventType, payload);
        }
    }

    private boolean hasJoinOpenedSignal(Map<String, Object> payload) {
        return bool(payload, "joinLinkOpened", "join_link_opened", "notificationOpened", "notification_opened", "pushOpened", "push_opened");
    }

    private boolean hasDeliveryFailure(Map<String, Object> payload) {
        String status = string(payload, "deliveryStatus", "notificationStatus", "pushDeliveryStatus");
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "FAILED".equals(normalized) || "UNDELIVERED".equals(normalized) || "BOUNCED".equals(normalized);
    }

    private boolean isTooEarlyForNoShow(Map<String, Object> payload) {
        if (!bool(payload, "notificationDelivered", "notification_delivered", "pushDelivered", "push_delivered")) {
            return false;
        }
        OffsetDateTime scheduled = parseDate(string(payload, "scheduledAt", "scheduled_at"));
        if (scheduled == null) {
            return false;
        }
        return Duration.between(scheduled, OffsetDateTime.now()).toMinutes() < 3;
    }

    private boolean isStuckWaitingRoom(Map<String, Object> payload) {
        OffsetDateTime enteredAt = parseDate(string(payload, "waitingRoomEnteredAt", "waiting_room_entered_at", "enteredAt"));
        if (enteredAt == null) {
            return false;
        }
        return Duration.between(enteredAt, OffsetDateTime.now()).toMinutes() >= 10;
    }

    private boolean bool(Map<String, Object> payload, String... keys) {
        String raw = string(payload, keys);
        if (raw == null) {
            return false;
        }
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    private OffsetDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> linkagePayload(Map<String, Object> payload) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("client", string(payload, "patientCpid", "patientId", "patient_id"));
        links.put("provider", string(payload, "providerId", "provider_id"));
        links.put("facility", string(payload, "facilityId", "facility_id"));
        links.put("session", string(payload, "id", "sessionId"));
        links.put("appointment", string(payload, "appointmentId", "appointment_id"));
        links.put("referral", string(payload, "referralId", "referral_id"));
        links.put("prescription", string(payload, "prescriptionId", "prescription_id"));
        links.put("lab_request", string(payload, "labRequestId", "lab_request_id"));
        links.put("payment_or_claim", string(payload, "claimId", "paymentId", "billingId"));
        links.put("helpdesk_ticket", string(payload, "ticketId", "supportTicketId"));
        links.put("followup_task", string(payload, "followupTaskId", "taskId"));
        links.put("linkedAt", OffsetDateTime.now().toString());
        links.values().removeIf(v -> v == null || (v instanceof String s && s.isBlank()));
        return links;
    }

    private String enforceSensitiveContentPolicy(String channel, String message) {
        if (message == null || message.isBlank()) {
            return SAFE_DEFAULT_MESSAGE;
        }
        if (!INSECURE_CHANNELS.contains(channel.toUpperCase(Locale.ROOT))) {
            return message;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String token : SENSITIVE_TERMS) {
            if (lower.contains(token)) {
                return SAFE_DEFAULT_MESSAGE;
            }
        }
        return message;
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
