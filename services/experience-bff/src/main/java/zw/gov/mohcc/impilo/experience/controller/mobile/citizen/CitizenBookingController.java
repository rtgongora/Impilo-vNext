package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Citizen booking transaction endpoints (distinct from confirmed appointments).
 * GET  /internal/v1/mobile/citizen/bookings
 * GET  /internal/v1/mobile/citizen/bookings/{id}
 * POST /internal/v1/mobile/citizen/bookings/{id}/cancel
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/bookings")
public class CitizenBookingController {

    private final BookingServiceClient bookingServiceClient;

    public CitizenBookingController(BookingServiceClient bookingServiceClient) {
        this.bookingServiceClient = bookingServiceClient;
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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", cancelled != null ? cancelled : Map.of("id", id.toString(), "bookingStatus", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
