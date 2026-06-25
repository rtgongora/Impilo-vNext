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
 * failures degrade to 502 with an error envelope rather than being masked as success.</p>
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

    /** Drive a guarded imaging-workflow transition (accept/start/complete/...). */
    @PostMapping("/orders/{orderId}/imaging-transition")
    public ResponseEntity<Map<String, Object>> imagingTransition(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return proxy(requestId, correlationId, () -> orosClient.imagingTransition(orderId, target, reason));
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

    private static final java.util.Set<String> REPORT_ACTIONS =
            java.util.Set.of("preliminary", "final", "amend", "addendum");

    /** Author/amend a report for an order (action = preliminary|final|amend|addendum). */
    @PostMapping("/results/{orderId}/report/{action}")
    public ResponseEntity<Map<String, Object>> report(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @PathVariable String action,
            @RequestBody Map<String, Object> body) {
        if (!REPORT_ACTIONS.contains(action)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "BAD_ACTION", "message", "unknown report action: " + action),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        return proxy(requestId, correlationId, () -> orosClient.postReport(orderId, action, body));
    }

    /** Release a report version. */
    @PostMapping("/results/{resultId}/release")
    public ResponseEntity<Map<String, Object>> releaseReport(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String resultId,
            @RequestBody(required = false) Map<String, Object> body) {
        String note = body != null && body.get("note") != null ? body.get("note").toString() : null;
        return proxy(requestId, correlationId, () -> orosClient.releaseReport(resultId, note));
    }

    /** Full report version chain for an order. */
    @GetMapping("/results/{orderId}/versions")
    public ResponseEntity<Map<String, Object>> reportVersions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId) {
        return proxy(requestId, correlationId, () -> orosClient.orderReportVersions(orderId));
    }

    /** Acknowledge a critical result. */
    @PostMapping("/results/{resultId}/critical/ack")
    public ResponseEntity<Map<String, Object>> acknowledgeCritical(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String resultId,
            @RequestBody(required = false) Map<String, Object> body) {
        String note = body != null && body.get("note") != null ? body.get("note").toString() : null;
        return proxy(requestId, correlationId, () -> orosClient.acknowledgeCriticalResult(resultId, note));
    }

    /** Unacknowledged critical results (critical-results dashboard). */
    @GetMapping("/critical-unacknowledged")
    public ResponseEntity<Map<String, Object>> criticalUnacknowledged(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(requestId, correlationId, orosClient::criticalUnacknowledged);
    }

    /** Operational reconciliation summary (stuck-order buckets + unacked critical). */
    @GetMapping("/reconcile-summary")
    public ResponseEntity<Map<String, Object>> reconcileSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(requestId, correlationId, orosClient::reconcileDiagnosticsSummary);
    }

    /** Imaging workload/turnaround distribution. */
    @GetMapping("/turnaround")
    public ResponseEntity<Map<String, Object>> turnaround(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(requestId, correlationId, orosClient::turnaround);
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
