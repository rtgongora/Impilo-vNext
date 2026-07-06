package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PlatformOriginGovernanceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF proxy for the workforce-governance-service Platform Origin API
 * (country operations, two-person platform action approvals/execution,
 * national appointments). Fail-closed: any upstream failure surfaces as
 * HTTP 502 PLATFORM_ORIGIN_UNAVAILABLE — never a fabricated success.
 */
@RestController
@RequestMapping("/internal/v1/platform-origin")
public class PlatformOriginController {

    private static final Logger log = LoggerFactory.getLogger(PlatformOriginController.class);

    private final PlatformOriginGovernanceClient client;

    public PlatformOriginController(PlatformOriginGovernanceClient client) {
        this.client = client;
    }

    @PostMapping("/country-operations")
    public ResponseEntity<Map<String, Object>> initiateCountryOperation(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.initiateCountryOperation(body), HttpStatus.CREATED, requestId, correlationId);
    }

    @PostMapping("/actions/{accessRequestId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable UUID accessRequestId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.approveAction(accessRequestId, body != null ? body : Map.of()),
                HttpStatus.OK, requestId, correlationId);
    }

    @PostMapping("/actions/{accessRequestId}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable UUID accessRequestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.executeAction(accessRequestId), HttpStatus.OK, requestId, correlationId);
    }

    @GetMapping("/country-operations")
    public ResponseEntity<Map<String, Object>> listCountryOperations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(client::listCountryOperations, HttpStatus.OK, requestId, correlationId);
    }

    /** Pending platform-action approvals inbox (PLATFORM_ACTION requests, default status PENDING). */
    @GetMapping("/actions")
    public ResponseEntity<Map<String, Object>> listPendingActions(
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listPendingActions(status), HttpStatus.OK, requestId, correlationId);
    }

    /** Who-approved projection for a platform action's two-person approvals. */
    @GetMapping("/actions/{accessRequestId}/approvals")
    public ResponseEntity<Map<String, Object>> listActionApprovals(
            @PathVariable UUID accessRequestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getActionApprovals(accessRequestId), HttpStatus.OK, requestId, correlationId);
    }

    @GetMapping("/country-operations/{id}")
    public ResponseEntity<Map<String, Object>> getCountryOperation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getCountryOperation(id), HttpStatus.OK, requestId, correlationId);
    }

    @GetMapping("/country-operations/{id}/appointments")
    public ResponseEntity<Map<String, Object>> listAppointments(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listAppointments(id), HttpStatus.OK, requestId, correlationId);
    }

    @PostMapping("/country-operations/{id}/appointments")
    public ResponseEntity<Map<String, Object>> initiateAppointment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.initiateAppointment(id, body), HttpStatus.CREATED, requestId, correlationId);
    }

    @DeleteMapping("/country-operations/{id}/appointments/{appointmentId}")
    public ResponseEntity<Map<String, Object>> initiateRevocation(
            @PathVariable UUID id,
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.initiateRevocation(id, appointmentId, body),
                HttpStatus.ACCEPTED, requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> supplier,
            HttpStatus successStatus,
            String requestId,
            String correlationId) {
        try {
            return ResponseEntity.status(successStatus).body(wrap(supplier.get(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("Platform origin proxy failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data != null ? data : Map.of());
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "PLATFORM_ORIGIN_UNAVAILABLE",
                        "message", message != null ? message : "platform origin governance unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
