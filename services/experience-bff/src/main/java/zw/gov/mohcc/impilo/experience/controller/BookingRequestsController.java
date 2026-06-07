package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider inbox — pending booking requests for a facility.
 */
@RestController
@RequestMapping("/internal/v1/booking-requests")
public class BookingRequestsController {

    private final BookingServiceClient bookingServiceClient;

    public BookingRequestsController(BookingServiceClient bookingServiceClient) {
        this.bookingServiceClient = bookingServiceClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPendingRequests(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityHeader,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "provider_id") String providerId,
            @RequestParam(defaultValue = "REQUESTED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String resolvedFacility = facilityId != null && !facilityId.isBlank() ? facilityId : facilityHeader;
        int limit = Math.min(size, 100);
        JsonNode requests = bookingServiceClient.listBookings(
                null, resolvedFacility, providerId, status, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", requests);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "inbox_status", status));
        return ResponseEntity.ok(response);
    }
}
