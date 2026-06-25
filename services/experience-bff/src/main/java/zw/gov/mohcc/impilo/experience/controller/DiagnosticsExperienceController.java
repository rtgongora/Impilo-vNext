package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.Map;

/**
 * Governed BFF surface for the OROS diagnostics journey — order tracking, the requester results
 * inbox, the imaging worklist, operational reconciliation summary, and honest integration status.
 *
 * <p>Thin proxy over {@link OrosServiceClient}; trust headers are forwarded to OROS by the shared
 * RestTemplate interceptor. Responses use the standard {@code {data, meta}} envelope; upstream
 * failures degrade to 502 with an error envelope (never a fake-success).</p>
 */
@RestController
@RequestMapping("/internal/v1/diagnostics")
public class DiagnosticsExperienceController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsExperienceController.class);

    private final OrosServiceClient orosClient;

    public DiagnosticsExperienceController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    /** Create a diagnostic order draft. */
    @PostMapping("/orders/draft")
    public ResponseEntity<Map<String, Object>> createDraft(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.createDraft(body));
    }

    /** Submit a draft order. */
    @PostMapping("/orders/{orderId}/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId) {
        return proxy(requestId, correlationId, () -> orosClient.submitOrder(orderId));
    }

    /** Assign a routing destination to an order (referral). */
    @PostMapping("/orders/{orderId}/route")
    public ResponseEntity<Map<String, Object>> route(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.routeOrder(orderId, body));
    }

    /** Diagnostic order tracking list. */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orders(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "client", required = false) String client,
            @RequestParam(name = "requester", required = false) String requester,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type) {
        return proxy(requestId, correlationId, () -> orosClient.listOrders(client, requester, status, type));
    }

    /** Imaging-team worklist. */
    @GetMapping("/imaging-worklist")
    public ResponseEntity<Map<String, Object>> imagingWorklist(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "states", required = false) String states) {
        return proxy(requestId, correlationId, () -> orosClient.imagingWorklist(states));
    }

    /** Requester results inbox. */
    @GetMapping("/results-inbox")
    public ResponseEntity<Map<String, Object>> resultsInbox(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "requester", required = false) String requester) {
        return proxy(requestId, correlationId, () -> orosClient.resultsInbox(requester));
    }

    /** Operational reconciliation summary (stuck-order buckets + unacked critical). */
    @GetMapping("/reconcile-summary")
    public ResponseEntity<Map<String, Object>> reconcileSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(requestId, correlationId, orosClient::reconcileDiagnosticsSummary);
    }

    /** Honest configured/not-configured integration status. */
    @GetMapping("/integration-status")
    public ResponseEntity<Map<String, Object>> integrationStatus(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(requestId, correlationId, orosClient::integrationStatus);
    }

    private interface UpstreamCall {
        JsonNode get();
    }

    private ResponseEntity<Map<String, Object>> proxy(String requestId, String correlationId, UpstreamCall call) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS diagnostics fetch failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "OROS_UNAVAILABLE",
                            "message", e.getMessage() != null ? e.getMessage() : "OROS upstream unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
