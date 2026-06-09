package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsWorkflowService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared booking-first appointment check-in used by web scheduling and citizen mobile surfaces.
 */
@Service
public class AppointmentCheckInService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCheckInService.class);

    private final BookingServiceClient bookingServiceClient;
    private final PctServiceClient pctClient;
    private final AppointmentCommsWorkflowService appointmentComms;

    public AppointmentCheckInService(BookingServiceClient bookingServiceClient,
                                     PctServiceClient pctClient,
                                     AppointmentCommsWorkflowService appointmentComms) {
        this.bookingServiceClient = bookingServiceClient;
        this.pctClient = pctClient;
        this.appointmentComms = appointmentComms;
    }

    public ResponseEntity<Map<String, Object>> checkIn(
            UUID appointmentId,
            String requestId,
            String correlationId) {
        try {
            JsonNode appointment = bookingServiceClient.getAppointment(appointmentId.toString());
            if (appointment == null || appointment.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "APPOINTMENT_NOT_FOUND", "message", "Appointment not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }

            String facilityId = firstNonBlank(
                    textOr(appointment, "facilityId"),
                    textOr(appointment, "facility_id"),
                    textOr(appointment.path("attributes"), "facilityId"));
            String patientId = firstNonBlank(
                    textOr(appointment, "patientId"),
                    textOr(appointment, "patient_id"),
                    textOr(appointment.path("attributes"), "patientId"));

            if (facilityId == null || facilityId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "VALIDATION", "message", "facility_id is required on appointment"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }

            UUID facilityUuid = UUID.fromString(facilityId.trim());
            JsonNode queues = pctClient.listQueues(facilityUuid, null);
            UUID queueUuid = findScheduledQueueId(queues);

            JsonNode checkedIn;
            try {
                checkedIn = bookingServiceClient.checkInAppointment(
                        appointmentId.toString(),
                        queueUuid != null ? queueUuid.toString() : null);
                if (checkedIn == null || checkedIn.isNull()) {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                            "error", Map.of(
                                    "code", "BOOKING_CHECK_IN_FAILED",
                                    "message", "Booking-service did not persist CHECKED_IN"),
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (Exception bookingEx) {
                log.warn("Booking-service check-in failed for appointment {}: {}", appointmentId, bookingEx.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", Map.of(
                                "code", "BOOKING_CHECK_IN_FAILED",
                                "message", "Appointment check-in failed: " + bookingEx.getMessage()),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }

            String encounterId = firstNonBlank(
                    textOr(checkedIn, "encounterId"),
                    textOr(checkedIn, "encounter_id"));
            String queueTokenId = firstNonBlank(
                    textOr(checkedIn, "queueTokenId"),
                    textOr(checkedIn, "queue_token_id"));

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.put("appointment_id", appointmentId.toString());
            if (patientId != null) {
                meta.put("patient_id", patientId);
            }
            meta.put("booking_status", textOr(checkedIn, "status"));
            if (encounterId != null) {
                meta.put("encounter_id", encounterId);
                meta.put("core_transaction_id", CoreTransactionCompositionService.encounterTransactionId(encounterId));
                try {
                    long encounterNumeric = Long.parseLong(encounterId.trim());
                    JsonNode encounterNode = pctClient.getEncounter(encounterNumeric);
                    if (encounterNode != null && encounterNode.has("journeyId") && !encounterNode.get("journeyId").isNull()) {
                        meta.put("journey_id", encounterNode.get("journeyId").asText());
                    }
                } catch (NumberFormatException ignored) {
                    // Non-numeric encounter refs skip PCT journey lookup.
                }
            }
            if (queueTokenId != null) {
                meta.put("queue_token_id", queueTokenId);
            }
            if (queueUuid != null) {
                meta.put("queue_id", queueUuid.toString());
            }

            appointmentComms.onAppointmentCheckedIn(checkedIn);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "appointment_id", appointmentId.toString(),
                    "status", "CHECKED_IN",
                    "encounter_id", encounterId != null ? encounterId : "",
                    "queue_token_id", queueTokenId != null ? queueTokenId : ""));
            response.put("meta", meta);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", ex.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception ex) {
            log.warn("Appointment check-in failed for {}: {}", appointmentId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "CHECK_IN_FAILED", "message", "Appointment check-in failed"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static UUID findScheduledQueueId(JsonNode queues) {
        if (queues == null || !queues.isArray()) {
            return null;
        }
        for (JsonNode queue : queues) {
            String queueType = textOr(queue, "queueType");
            if (queueType == null) {
                queueType = textOr(queue, "queue_type");
            }
            if (queueType != null && queueType.toUpperCase().contains("WALK")) {
                String queueId = textOr(queue, "queueId");
                if (queueId == null) {
                    queueId = textOr(queue, "queue_id");
                }
                if (queueId != null) {
                    return UUID.fromString(queueId);
                }
            }
        }
        if (queues.size() > 0) {
            JsonNode first = queues.get(0);
            String queueId = firstNonBlank(textOr(first, "queueId"), textOr(first, "queue_id"));
            if (queueId != null) {
                return UUID.fromString(queueId);
            }
        }
        return null;
    }

    private static String textOr(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value != null && !value.isBlank() ? value : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
