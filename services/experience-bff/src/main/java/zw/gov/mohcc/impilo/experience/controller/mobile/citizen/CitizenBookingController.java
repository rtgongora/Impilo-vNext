package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsWorkflowService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Citizen booking transaction endpoints (distinct from confirmed appointments).
 * GET  /internal/v1/mobile/citizen/bookings
 * GET  /internal/v1/mobile/citizen/bookings/{id}
 * POST /internal/v1/mobile/citizen/bookings
 * POST /internal/v1/mobile/citizen/bookings/{id}/cancel
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/bookings")
public class CitizenBookingController {

    private final BookingServiceClient bookingServiceClient;
    private final AppointmentCommsWorkflowService appointmentComms;

    public CitizenBookingController(BookingServiceClient bookingServiceClient,
                                    AppointmentCommsWorkflowService appointmentComms) {
        this.bookingServiceClient = bookingServiceClient;
        this.appointmentComms = appointmentComms;
    }

    public record CreateBookingBody(
            @NotBlank String facilityId,
            @NotBlank String appointmentType,
            @NotBlank String preferredDate,
            String reason
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody CreateBookingBody body) {

        Map<String, Object> bookingData = new LinkedHashMap<>();
        bookingData.put("facilityId", body.facilityId());
        bookingData.put("appointmentType", body.appointmentType());
        bookingData.put("bookingType", body.appointmentType());
        bookingData.put("preferredDate", body.preferredDate());
        bookingData.put("preferredStartTime", body.preferredDate());
        bookingData.put("reason", body.reason());
        bookingData.put("reasonForBooking", body.reason());

        JsonNode result = bookingServiceClient.createCitizenBooking(actorId, bookingData);
        if (result != null) {
            appointmentComms.onBookingCreated(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
        JsonNode bookings = bookingServiceClient.listBookings(actorId, null, null, status, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", bookings);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode booking = bookingServiceClient.getBooking(id.toString());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", booking);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.containsKey("reason") ? body.get("reason").toString() : "Cancelled";
        JsonNode cancelled = bookingServiceClient.cancelBooking(id.toString(), reason);
        if (cancelled != null) {
            appointmentComms.onBookingCancelled(cancelled, reason, AppointmentCommsWorkflowService.INITIATOR_CITIZEN);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", cancelled != null ? cancelled : Map.of("id", id.toString(), "bookingStatus", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
