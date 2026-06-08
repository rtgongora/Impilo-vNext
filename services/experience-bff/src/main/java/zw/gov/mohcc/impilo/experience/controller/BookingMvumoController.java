package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mvumo consent/agreement/authorisation steps for a booking transaction.
 */
@RestController
@RequestMapping("/internal/v1/bookings/{id}/mvumo")
public class BookingMvumoController {

    private final BookingServiceClient bookingServiceClient;

    public BookingMvumoController(BookingServiceClient bookingServiceClient) {
        this.bookingServiceClient = bookingServiceClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMvumoStatus(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode status = bookingServiceClient.getMvumoStatus(id.toString());
        return ok(status, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestMvumo(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        JsonNode result = bookingServiceClient.requestMvumo(id.toString(), body);
        return ok(result, requestId, correlationId, HttpStatus.OK);
    }

    private static ResponseEntity<Map<String, Object>> ok(
            Object data, String requestId, String correlationId, HttpStatus status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(status).body(response);
    }
}
