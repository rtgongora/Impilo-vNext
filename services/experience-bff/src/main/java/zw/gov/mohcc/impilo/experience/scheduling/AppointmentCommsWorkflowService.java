package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.facility.FacilityNameResolver;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Two-way appointment/booking comms — citizen and provider notifications on lifecycle events.
 * Non-blocking: callers invoke after successful booking-service mutations; booking outbox poller
 * provides a backup event-driven path.
 */
@Service
public class AppointmentCommsWorkflowService {

    public static final String INITIATOR_CITIZEN = "CITIZEN";
    public static final String INITIATOR_STAFF = "STAFF";

    private static final Logger log = LoggerFactory.getLogger(AppointmentCommsWorkflowService.class);
    private static final String MESSAGE_KIND = "APPOINTMENT";
    private static final Duration REMINDER_WINDOW = Duration.ofHours(24);
    private static final Duration REMINDER_TOLERANCE = Duration.ofHours(1);
    private static final Set<String> SENSITIVE_TERMS = Set.of(
            "diagnosis", "hiv", "pregnancy", "medication", "mental", "test result", "viral load");
    private static final Set<String> INSECURE_CHANNELS = Set.of("SMS", "WHATSAPP", "EMAIL");
    private static final String SAFE_SMS_MESSAGE =
            "You have an appointment update from your care team. Open Impilo for details.";

    private final NotificationServiceClient notificationClient;
    private final FacilityNameResolver facilityNameResolver;
    private final AppointmentProviderRecipientResolver providerRecipientResolver;
    private final AppointmentCommsHistoryStore historyStore;
    private final AppointmentReminderReceiptStore reminderReceiptStore;
    private final ZoneId displayZone;
    private final DateTimeFormatter displayFormatter;

    public AppointmentCommsWorkflowService(
            NotificationServiceClient notificationClient,
            FacilityNameResolver facilityNameResolver,
            AppointmentProviderRecipientResolver providerRecipientResolver,
            AppointmentCommsHistoryStore historyStore,
            AppointmentReminderReceiptStore reminderReceiptStore,
            @Value("${impilo.scheduling.display-timezone:Africa/Harare}") String displayTimezone) {
        this.notificationClient = notificationClient;
        this.facilityNameResolver = facilityNameResolver;
        this.providerRecipientResolver = providerRecipientResolver;
        this.historyStore = historyStore;
        this.reminderReceiptStore = reminderReceiptStore;
        this.displayZone = ZoneId.of(displayTimezone);
        this.displayFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)
                .withZone(displayZone);
    }

    public void onBookingCreated(JsonNode booking) {
        Map<String, String> vars = varsFromBooking(booking);
        notifyCitizen(booking, "APPOINTMENT_CITIZEN_REQUESTED", "IN_APP", vars);
        notifyCitizen(booking, "APPOINTMENT_CITIZEN_REQUESTED_SMS", "SMS", vars);
        notifyProvider(booking, "APPOINTMENT_PROVIDER_NEW_REQUEST", "PUSH", vars);
        historyStore.recordLifecycle("booking.created", lifecyclePayload(booking), "sent");
    }

    public void onBookingApproved(JsonNode booking) {
        Map<String, String> vars = varsFromBooking(booking);
        notifyCitizen(booking, "APPOINTMENT_CITIZEN_CONFIRMED", "IN_APP", vars);
        notifyCitizen(booking, "APPOINTMENT_CITIZEN_CONFIRMED_SMS", "SMS", vars);
        historyStore.recordLifecycle("booking.confirmed", lifecyclePayload(booking), "sent");
    }

    public void onBookingRejected(JsonNode booking, String reason) {
        Map<String, String> vars = varsFromBooking(booking);
        vars.put("reason", safe(reason, "Booking not approved"));
        notifyCitizen(booking, "APPOINTMENT_CITIZEN_REJECTED", "IN_APP", vars);
        historyStore.recordLifecycle("booking.rejected", lifecyclePayload(booking), "sent");
    }

    public void onBookingCancelled(JsonNode booking, String reason) {
        onBookingCancelled(booking, reason, INITIATOR_CITIZEN);
    }

    public void onBookingCancelled(JsonNode booking, String reason, String initiator) {
        Map<String, String> vars = varsFromBooking(booking);
        vars.put("reason", safe(reason, "Cancelled"));
        notifyCitizen(booking, "APPOINTMENT_CITIZEN_CANCELLED", "IN_APP", vars);
        if (INITIATOR_CITIZEN.equalsIgnoreCase(initiator)) {
            notifyProvider(booking, "APPOINTMENT_PROVIDER_CANCELLED", "PUSH", vars);
        } else {
            notifyProvider(booking, "APPOINTMENT_PROVIDER_CANCELLED_BY_STAFF", "PUSH", vars);
        }
        historyStore.recordLifecycle("booking.cancelled", lifecyclePayload(booking), "sent");
    }

    public void onAppointmentCreated(JsonNode appointment) {
        String status = text(appointment, "status");
        if ("REQUESTED".equalsIgnoreCase(status)) {
            onBookingCreated(appointment);
            return;
        }
        if ("SCHEDULED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
            Map<String, String> vars = varsFromAppointment(appointment);
            notifyCitizen(appointment, "APPOINTMENT_CITIZEN_CONFIRMED", "IN_APP", vars);
            notifyCitizen(appointment, "APPOINTMENT_CITIZEN_CONFIRMED_SMS", "SMS", vars);
            historyStore.recordLifecycle("appointment.booked", lifecyclePayload(appointment), "sent");
        }
    }

    public void onAppointmentConfirmed(JsonNode appointment) {
        Map<String, String> vars = varsFromAppointment(appointment);
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_CONFIRMED", "IN_APP", vars);
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_CONFIRMED_SMS", "SMS", vars);
        historyStore.recordLifecycle("appointment.confirmed", lifecyclePayload(appointment), "sent");
    }

    public void onAppointmentCancelled(JsonNode appointment, String reason) {
        onAppointmentCancelled(appointment, reason, INITIATOR_STAFF);
    }

    public void onAppointmentCancelled(JsonNode appointment, String reason, String initiator) {
        Map<String, String> vars = varsFromAppointment(appointment);
        vars.put("reason", safe(reason, "Cancelled"));
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_CANCELLED", "IN_APP", vars);
        if (INITIATOR_CITIZEN.equalsIgnoreCase(initiator)) {
            notifyProvider(appointment, "APPOINTMENT_PROVIDER_CANCELLED", "PUSH", vars);
        } else {
            notifyProvider(appointment, "APPOINTMENT_PROVIDER_CANCELLED_BY_STAFF", "PUSH", vars);
        }
        historyStore.recordLifecycle("appointment.cancelled", lifecyclePayload(appointment), "sent");
    }

    public void onAppointmentRescheduled(JsonNode appointment) {
        Map<String, String> vars = varsFromAppointment(appointment);
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_RESCHEDULED", "IN_APP", vars);
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_RESCHEDULED_SMS", "SMS", vars);
        notifyProvider(appointment, "APPOINTMENT_PROVIDER_RESCHEDULED", "PUSH", vars);
        historyStore.recordLifecycle("appointment.rescheduled", lifecyclePayload(appointment), "sent");
    }

    public void onAppointmentCheckedIn(JsonNode appointment) {
        Map<String, String> vars = varsFromAppointment(appointment);
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_CHECKED_IN", "IN_APP", vars);
        notifyProvider(appointment, "APPOINTMENT_PROVIDER_CHECKED_IN", "PUSH", vars);
        historyStore.recordLifecycle("appointment.checked_in", lifecyclePayload(appointment), "sent");
    }

    public void sendCitizenToProviderMessage(JsonNode appointment, String message, String senderActorId) {
        Map<String, String> vars = varsFromAppointment(appointment);
        vars.put("message", safe(message, ""));
        notifyProvider(appointment, "APPOINTMENT_PROVIDER_MESSAGE", "PUSH", vars);
        historyStore.recordMessage(text(appointment, "id"), "citizen_to_provider", message, senderActorId);
    }

    public void sendProviderToCitizenMessage(JsonNode appointment, String message, String senderActorId) {
        Map<String, String> vars = varsFromAppointment(appointment);
        vars.put("message", safe(message, ""));
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_MESSAGE", "IN_APP", vars);
        notifyCitizen(appointment, "APPOINTMENT_CITIZEN_MESSAGE_SMS", "SMS", vars);
        historyStore.recordMessage(text(appointment, "id"), "provider_to_citizen", message, senderActorId);
    }

    public List<Map<String, Object>> listMessages(String appointmentId) {
        return historyStore.listMessages(appointmentId);
    }

    public int dispatchUpcomingReminders(Iterable<JsonNode> appointments) {
        int sent = 0;
        Instant now = Instant.now();
        for (JsonNode appt : appointments) {
            Instant scheduled = parseInstant(text(appt, "scheduledAt", "scheduled_at", "startTime", "start_time"));
            if (scheduled == null) {
                continue;
            }
            String status = text(appt, "status");
            if (status == null || (!status.equalsIgnoreCase("CONFIRMED") && !status.equalsIgnoreCase("SCHEDULED"))) {
                continue;
            }
            Duration until = Duration.between(now, scheduled);
            if (until.isNegative() || until.minus(REMINDER_WINDOW).abs().compareTo(REMINDER_TOLERANCE) > 0) {
                continue;
            }
            String dedupKey = text(appt, "id") + "@" + scheduled;
            if (!reminderReceiptStore.tryClaim(dedupKey)) {
                continue;
            }
            Map<String, String> vars = varsFromAppointment(appt);
            notifyCitizen(appt, "APPOINTMENT_CITIZEN_REMINDER", "IN_APP", vars);
            notifyCitizen(appt, "APPOINTMENT_CITIZEN_REMINDER_SMS", "SMS", vars);
            historyStore.recordLifecycle("appointment.reminder", lifecyclePayload(appt), "sent");
            sent++;
        }
        return sent;
    }

    public void processOutboxEvent(String eventType, Map<String, Object> payload, long eventId) {
        String eventKey = eventType + ":" + eventId;
        if (!historyStore.markOutboxProcessed(eventKey)) {
            return;
        }
        JsonNode node = toJsonNode(payload);
        switch (eventType) {
            case "booking.created" -> onBookingCreated(node);
            case "booking.confirmed" -> onBookingApproved(node);
            case "booking.rejected" -> onBookingRejected(node, string(payload, "reason"));
            case "booking.cancelled" -> onBookingCancelled(node, string(payload, "reason"), INITIATOR_CITIZEN);
            case "appointment.booked" -> onAppointmentCreated(node);
            default -> log.debug("No appointment comms handler for outbox event {}", eventType);
        }
    }

    private void notifyCitizen(JsonNode node, String templateKey, String channel, Map<String, String> variables) {
        String recipient = firstNonBlank(
                text(node, "patientCpid", "patient_cpid", "clientId", "client_id"),
                text(node, "patientId", "patient_id"));
        if (recipient == null) {
            log.debug("Skip citizen notification {} — no recipient", templateKey);
            return;
        }
        send(templateKey, channel, recipient, recipient, recipient, variables);
    }

    private void notifyProvider(JsonNode node, String templateKey, String channel, Map<String, String> variables) {
        String providerId = text(node, "providerId", "provider_id");
        String facilityId = text(node, "facilityId", "facility_id");
        List<String> recipients = providerRecipientResolver.resolve(providerId, facilityId);
        if (recipients.isEmpty()) {
            log.debug("Skip provider notification {} — no provider/facility", templateKey);
            return;
        }
        String patientRef = text(node, "patientCpid", "patient_cpid", "clientId", "client_id");
        String routingTo = providerId != null ? "provider:" + providerId
                : "scheduling:facility:" + facilityId;
        for (String inboxRecipient : recipients) {
            send(templateKey, channel, routingTo, inboxRecipient, patientRef, variables);
        }
    }

    private void send(String templateKey, String channel, String to, String inboxRecipient,
                      String patientRef, Map<String, String> variables) {
        Map<String, String> safeVars = sanitizeForChannel(channel, variables);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("templateKey", templateKey);
            body.put("channel", channel);
            body.put("to", to);
            body.put("inboxRecipient", inboxRecipient);
            body.put("variables", safeVars);
            if (patientRef != null && !patientRef.isBlank()) {
                body.put("patientRef", patientRef);
            }
            body.put("messageKind", MESSAGE_KIND);
            notificationClient.sendNotification(body);
            historyStore.recordNotification(templateKey, channel, templateKey, "SENT",
                    Map.of("to", to, "inboxRecipient", inboxRecipient));
        } catch (Exception ex) {
            log.warn("Appointment comms failed template={} channel={} to={}: {}", templateKey, channel, to, ex.getMessage());
            historyStore.recordNotification(templateKey, channel, templateKey, "FAILED",
                    Map.of("to", to, "error", ex.getMessage()));
        }
    }

    private Map<String, String> sanitizeForChannel(String channel, Map<String, String> variables) {
        if (!INSECURE_CHANNELS.contains(channel.toUpperCase(Locale.ROOT))) {
            return variables;
        }
        Map<String, String> copy = new LinkedHashMap<>(variables);
        String message = copy.getOrDefault("message", "");
        String combined = (message + " " + copy.values()).toLowerCase(Locale.ROOT);
        for (String term : SENSITIVE_TERMS) {
            if (combined.contains(term)) {
                copy.put("message", SAFE_SMS_MESSAGE);
                return copy;
            }
        }
        return copy;
    }

    private Map<String, String> varsFromBooking(JsonNode node) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("bookingNumber", safe(text(node, "bookingNumber", "booking_number"), "—"));
        vars.put("appointmentType", safe(text(node, "bookingType", "booking_type", "appointmentType"), "Appointment"));
        vars.put("facilityName", resolveFacilityName(node));
        vars.put("providerName", safe(text(node, "providerName", "provider_name"), "your care team"));
        vars.put("scheduledAt", formatScheduled(text(node, "preferredStartAt", "preferred_start_at", "scheduledAt", "scheduled_at")));
        vars.put("patientCpid", safe(text(node, "clientId", "client_id", "patientCpid", "patient_cpid"), "patient"));
        vars.put("appointmentId", safe(text(node, "linkedAppointmentId", "linked_appointment_id", "id"), ""));
        return vars;
    }

    private Map<String, String> varsFromAppointment(JsonNode node) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("appointmentId", safe(text(node, "id"), ""));
        vars.put("bookingNumber", safe(text(node, "appointmentNumber", "appointment_number"), vars.get("appointmentId")));
        vars.put("appointmentType", safe(text(node, "appointmentType", "appointment_type"), "Appointment"));
        vars.put("facilityName", resolveFacilityName(node));
        vars.put("providerName", safe(text(node, "providerName", "provider_name"), "your care team"));
        vars.put("scheduledAt", formatScheduled(text(node, "scheduledAt", "scheduled_at", "startTime", "start_time")));
        vars.put("patientCpid", safe(text(node, "patientCpid", "patient_cpid", "patientId", "patient_id", "clientId"), "patient"));
        return vars;
    }

    private String resolveFacilityName(JsonNode node) {
        String explicit = text(node, "facilityName", "facility_name");
        if (explicit != null && !explicit.isBlank() && !explicit.toLowerCase(Locale.ROOT).startsWith("facility ")) {
            return explicit;
        }
        String facilityId = text(node, "facilityId", "facility_id");
        if (facilityId != null) {
            return facilityNameResolver.resolve(facilityId);
        }
        return "your facility";
    }

    private String formatScheduled(String raw) {
        if (raw == null || raw.isBlank()) {
            return "the scheduled time";
        }
        try {
            Instant instant = Instant.parse(raw);
            ZonedDateTime zoned = instant.atZone(displayZone);
            return displayFormatter.format(zoned);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static Map<String, Object> lifecyclePayload(JsonNode node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", text(node, "id"));
        payload.put("patientCpid", text(node, "patientCpid", "patient_cpid", "clientId", "client_id"));
        payload.put("facilityId", text(node, "facilityId", "facility_id"));
        payload.put("status", text(node, "status"));
        return payload;
    }

    private static JsonNode toJsonNode(Map<String, Object> payload) {
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(payload);
    }

    private static String string(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        return payload.get(key).toString();
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String safe(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
