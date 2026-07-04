package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;

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
    private final PacsServiceClient pacsClient;

    public DiagnosticsExperienceController(OrosServiceClient orosClient, PacsServiceClient pacsClient) {
        this.orosClient = orosClient;
        this.pacsClient = pacsClient;
    }

    /**
     * Launch a governed DICOM viewer session for an order's linked study (criterion F).
     *
     * Resolves the order's study UID, finds the PACS study, and launches an audited viewer
     * session via the imaging service — returning the launch context (viewer URL).
     */
    @PostMapping("/orders/{orderId}/viewer")
    public ResponseEntity<Map<String, Object>> launchViewer(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId) {
        try {
            JsonNode order = orosClient.getOrder(orderId);
            String studyUid = order != null ? order.path("studyUid").asText(null) : null;
            if (studyUid == null || studyUid.isBlank()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", Map.of("code", "NO_LINKED_STUDY",
                                "message", "Order has no linked imaging study yet"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            JsonNode studies = pacsClient.searchStudies(Map.of("studyUid", studyUid));
            JsonNode study = (studies != null && studies.isArray() && studies.size() > 0) ? studies.get(0) : null;
            if (study == null || study.path("id").isMissingNode()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "STUDY_NOT_FOUND",
                                "message", "Linked study not found in PACS"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            String studyId = study.path("id").asText();
            JsonNode session = pacsClient.launchViewerSession(studyId,
                    Map.of("viewerType", "DICOMWEB_STACK", "contextRef", orderId));
            // Launch context: session + the identifiers the in-app viewer needs. viewerUrl is the
            // governed in-app viewer route (never a raw PACS URL).
            String patientCpid = study.path("patientCpid").asText(
                    order.path("patientCpid").asText(null));
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("session", session);
            data.put("studyUid", studyUid);
            data.put("governedStudyId", studyId);
            data.put("patientCpid", patientCpid);
            if (patientCpid != null && !patientCpid.isBlank()) {
                data.put("viewerUrl", "/ehr/" + java.net.URLEncoder.encode(patientCpid, java.nio.charset.StandardCharsets.UTF_8)
                        + "/imaging/viewer?studyUid=" + java.net.URLEncoder.encode(studyUid, java.nio.charset.StandardCharsets.UTF_8)
                        + "&governedStudyId=" + java.net.URLEncoder.encode(studyId, java.nio.charset.StandardCharsets.UTF_8));
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Viewer launch failed for order {}: {}", orderId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VIEWER_LAUNCH_FAILED",
                            "message", e.getMessage() != null ? e.getMessage() : "viewer launch failed"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
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

    /** Issue a printable order QR (patient-carried). */
    @PostMapping("/orders/{orderId}/printable")
    public ResponseEntity<Map<String, Object>> printable(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.printableOrder(orderId, body));
    }

    /** Claim a patient-carried order QR. */
    @PostMapping("/intake/qr/claim")
    public ResponseEntity<Map<String, Object>> qrClaim(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.qrClaim(body));
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

    /** Category fulfilment worklist (lab/procedure/assessment). */
    @GetMapping("/fulfilment-worklist")
    public ResponseEntity<Map<String, Object>> fulfilmentWorklist(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "type") String type,
            @RequestParam(name = "states", required = false) String states) {
        return proxy(requestId, correlationId, () -> orosClient.fulfilmentWorklist(type, states));
    }

    /** Drive a guarded fine-grained workflow transition (lab/procedure). */
    @PostMapping("/orders/{orderId}/workflow/transition")
    public ResponseEntity<Map<String, Object>> workflowTransition(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return proxy(requestId, correlationId, () -> orosClient.workflowTransition(orderId, target, reason));
    }

    /** Schedule a procedure/assessment appointment. */
    @PostMapping("/orders/{orderId}/workflow/schedule")
    public ResponseEntity<Map<String, Object>> workflowSchedule(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.workflowSchedule(orderId, body));
    }

    /** Record a specimen collected against a lab order. */
    @PostMapping("/orders/{orderId}/specimens")
    public ResponseEntity<Map<String, Object>> collectSpecimen(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.collectSpecimen(orderId, body));
    }

    /** Drive a specimen lifecycle action (label/dispatch/receive/reject/recollect). */
    @PostMapping("/specimens/{specimenId}/{action}")
    public ResponseEntity<Map<String, Object>> specimenAction(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String specimenId,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.specimenAction(specimenId, action, body));
    }

    /** Read a service-catalogue segment (services / orderables / specimen-config). */
    @GetMapping("/catalogue/{segment}")
    public ResponseEntity<Map<String, Object>> catalogue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String segment) {
        return proxy(requestId, correlationId, () -> orosClient.catalogueRead(segment));
    }

    /** Curate (upsert) an admin catalogue (service-catalogue / orderable-catalogue / specimen-config). */
    @PutMapping("/admin/catalogue/{adminPath}")
    public ResponseEntity<Map<String, Object>> saveCatalogue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String adminPath,
            @RequestBody(required = false) Map<String, Object> body) {
        return proxy(requestId, correlationId, () -> orosClient.catalogueWrite(adminPath, body));
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

    /** All results for an order (patient-file investigations view). */
    @GetMapping("/orders/{orderId}/results")
    public ResponseEntity<Map<String, Object>> orderResults(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId) {
        return proxy(requestId, correlationId, () -> orosClient.orderResults(orderId));
    }

    /** Structured lab observations for a result (value/unit/reference-range/abnormal+critical flags). */
    @GetMapping("/results/{resultId}/observations")
    public ResponseEntity<Map<String, Object>> resultObservations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String resultId) {
        return proxy(requestId, correlationId, () -> orosClient.resultObservations(resultId));
    }

    /** Specimens for a lab order (collection/dispatch/receipt/chain-of-custody). */
    @GetMapping("/orders/{orderId}/specimens")
    public ResponseEntity<Map<String, Object>> orderSpecimens(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId) {
        return proxy(requestId, correlationId, () -> orosClient.orderSpecimens(orderId));
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
