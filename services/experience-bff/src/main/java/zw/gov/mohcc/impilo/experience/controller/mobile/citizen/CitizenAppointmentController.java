package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.*;

/**
 * Citizen appointment endpoints — list appointments; create booking REQUESTED.
 * GET  /internal/v1/mobile/citizen/appointments
 * GET  /internal/v1/mobile/citizen/appointments/{id}
 * POST /internal/v1/mobile/citizen/appointments
 * POST /internal/v1/mobile/citizen/appointments/{id}/cancel
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/appointments")
public class CitizenAppointmentController {

    private final BookingServiceClient bookingServiceClient;

    public CitizenAppointmentController(BookingServiceClient bookingServiceClient) {
        this.bookingServiceClient = bookingServiceClient;
    }

    public record RequestAppointmentBody(
            @NotBlank String facilityId,
            @NotBlank String appointmentType,
            @NotBlank String preferredDate,
            String reason
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);

        JsonNode appointments = bookingServiceClient.listCitizenAppointments(actorId, status, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", enrichAppointmentsWithBookingId(appointments));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode appointment = bookingServiceClient.getAppointment(id.toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", enrichAppointmentWithBookingId(appointment));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody RequestAppointmentBody body) {

        Map<String, Object> bookingData = new LinkedHashMap<>();
        bookingData.put("facilityId", body.facilityId());
        bookingData.put("appointmentType", body.appointmentType());
        bookingData.put("bookingType", body.appointmentType());
        bookingData.put("preferredDate", body.preferredDate());
        bookingData.put("preferredStartTime", body.preferredDate());
        bookingData.put("reason", body.reason());
        bookingData.put("reasonForBooking", body.reason());

        JsonNode result = bookingServiceClient.createCitizenBooking(actorId, bookingData);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.containsKey("reason") ? body.get("reason").toString() : "Cancelled";

        JsonNode cancelled = bookingServiceClient.cancelAppointment(id.toString(), reason);

        Map<String, Object> response = new LinkedHashMap<>();
        Object data = cancelled != null ? enrichAppointmentWithBookingId(cancelled)
                : Map.of("id", id.toString(), "status", "CANCELLED");
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static Object enrichAppointmentWithBookingId(JsonNode appointment) {
        if (appointment == null || appointment.isNull()) {
            return appointment;
        }
        if (appointment.isObject()) {
            Map<String, Object> copy = new LinkedHashMap<>();
            appointment.fields().forEachRemaining(entry -> copy.put(entry.getKey(), entry.getValue()));
            String bookingId = firstNonBlank(
                    textOr(appointment, "bookingId"),
                    textOr(appointment, "booking_id"));
            if (bookingId != null) {
                copy.putIfAbsent("booking_id", bookingId);
                copy.putIfAbsent("bookingId", bookingId);
            }
            return copy;
        }
        return appointment;
    }

    private static Object enrichAppointmentsWithBookingId(JsonNode appointments) {
        if (appointments == null || appointments.isNull()) {
            return appointments;
        }
        if (appointments.isArray()) {
            List<Object> enriched = new ArrayList<>();
            appointments.forEach(node -> enriched.add(enrichAppointmentWithBookingId(node)));
            return enriched;
        }
        return enrichAppointmentWithBookingId(appointments);
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
