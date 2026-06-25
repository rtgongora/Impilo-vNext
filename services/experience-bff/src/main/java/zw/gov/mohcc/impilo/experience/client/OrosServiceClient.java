package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Orders &amp; Results Orchestration Service (OROS).
 *
 * <p>Delegates clinical order commands to OROS, which manages the canonical
 * order lifecycle including placement, routing, workstep generation, SLA
 * tracking, result capture, and acknowledgement workflows.</p>
 */
@Component
public class OrosServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrosServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.orosBaseUrl();
    }

    /**
     * Place a clinical order through OROS.
     *
     * @param orderType     LAB, IMAGING, PHARMACY, PROCEDURE
     * @param priority      ROUTINE, URGENT, STAT
     * @param patientCpid   the patient's CPID
     * @param encounterRef  the encounter reference (UUID string)
     * @param clinicalNotes free-text clinical notes
     * @param items         list of order item maps with code, displayName, quantity, etc.
     * @return the order summary from OROS
     */
    public JsonNode placeOrder(String orderType, String priority, String patientCpid,
                               String encounterRef, String clinicalNotes,
                               List<Map<String, Object>> items) {
        String url = baseUrl + "/v1/orders";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderType", orderType);
        if (priority != null) body.put("priority", priority);
        body.put("patientCpid", patientCpid);
        if (encounterRef != null) body.put("encounterRef", encounterRef);
        if (clinicalNotes != null) body.put("clinicalNotes", clinicalNotes);
        if (items != null && !items.isEmpty()) body.put("items", items);

        log.info("OROS: Placing order type={} for patient={}..., encounter={}",
                orderType, patientCpid.substring(0, Math.min(8, patientCpid.length())), encounterRef);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single order by its ID.
     *
     * @param orderId the OROS order ID
     * @return the order summary
     */
    public JsonNode getOrder(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get all orders for a patient by CPID.
     *
     * @param cpid patient's CPID
     * @return list of order summaries
     */
    public JsonNode getPatientOrders(String cpid) {
        String url = baseUrl + "/v1/orders/patient/" + cpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Cancel an order.
     *
     * @param orderId the order ID
     * @param reason  cancellation reason
     * @return the cancelled order
     */
    public JsonNode cancelOrder(String orderId, String reason) {
        String url = baseUrl + "/v1/orders/" + orderId + "/cancel";
        Map<String, Object> body = Map.of("reason", reason);

        log.info("OROS: Cancelling order={}, reason={}", orderId, reason);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get worklist for a facility (in-progress orders).
     *
     * @param facilityId the facility UUID
     * @param status     optional status filter
     * @return paged worklist payload ({@code items}, pagination fields) or legacy array
     */
    public JsonNode getWorklist(String facilityId, String status) {
        return getWorklists(facilityId, null, null, status, null, 0, 50);
    }

    /**
     * Get the OROS facility worklist with optional filters.
     */
    public JsonNode getWorklists(String facilityId, String workspaceId, String type,
                                 String status, String priority, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/worklists")
                .queryParam("facilityId", facilityId)
                .queryParam("page", page)
                .queryParam("size", size);
        if (workspaceId != null && !workspaceId.isBlank()) {
            builder.queryParam("workspaceId", workspaceId);
        }
        if (type != null && !type.isBlank()) {
            builder.queryParam("type", type);
        }
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (priority != null && !priority.isBlank()) {
            builder.queryParam("priority", priority);
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Accept an order from the facility worklist.
     */
    public JsonNode acceptWorklistItem(String orderId) {
        String url = baseUrl + "/v1/worklists/" + orderId + "/accept";
        log.info("OROS: Accepting worklist item={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Reject an order from the facility worklist.
     */
    public JsonNode rejectWorklistItem(String orderId, String reason) {
        String url = baseUrl + "/v1/worklists/" + orderId + "/reject";
        Map<String, Object> body = Map.of("reason", reason != null ? reason : "Rejected from experience UI");
        log.info("OROS: Rejecting worklist item={}, reason={}", orderId, reason);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Acknowledge an order result in OROS.
     *
     * @param orderId the OROS order ID
     * @param ackType DEPARTMENT, CLINICIAN, or CRITICAL
     * @param notes   optional acknowledgement notes
     * @return the acknowledgement response
     */
    public JsonNode acknowledgeOrder(String orderId, String ackType, String notes) {
        String url = baseUrl + "/v1/orders/" + orderId + "/ack";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ackType", ackType);
        if (notes != null) body.put("notes", notes);

        log.info("OROS: Acknowledging order={}, type={}", orderId, ackType);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get results for an OROS order.
     *
     * @param orderId the OROS order ID
     * @return list of result summaries
     */
    public JsonNode getOrderResults(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId + "/results";
        log.debug("OROS: Getting results for order={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Post a result for an OROS order.
     *
     * @param orderId      the OROS order ID
     * @param kind         result kind (LAB, IMAGING, PHARMACY, DOCUMENT)
     * @param summary      result summary object (will be serialized to JSON)
     * @param isCritical   whether this is a critical result
     * @return the result summary from OROS
     */
    public JsonNode postResult(String orderId, String kind, Object summary, boolean isCritical) {
        String url = baseUrl + "/v1/orders/" + orderId + "/results";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("summary", summary);
        body.put("isCritical", isCritical);

        log.info("OROS: Posting result for order={}, kind={}, critical={}", orderId, kind, isCritical);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get resulted orders for a patient (lab results).
     */
    public JsonNode getPatientResults(String cpid, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/orders/patient/" + cpid + "/results")
                .queryParam("page", page)
                .queryParam("size", size);
        log.debug("OROS: Getting results for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single result by order ID.
     */
    public JsonNode getResult(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId;
        log.debug("OROS: Getting result for order={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a diagnostic order draft.
     */
    public JsonNode createDraft(Map<String, Object> body) {
        String url = baseUrl + "/v1/orders/draft";
        log.info("OROS: Creating diagnostic draft type={}", body.get("orderType"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Submit a draft order (reserve accession, init imaging workflow, route).
     */
    public JsonNode submitOrder(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId + "/submit";
        log.info("OROS: Submitting order={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Issue a printable, OTP-protected QR for a patient-carried order.
     */
    public JsonNode printableOrder(String orderId, Map<String, Object> body) {
        String url = baseUrl + "/v1/orders/" + orderId + "/printable";
        log.info("OROS: Issuing printable QR for order={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Claim a patient-carried order QR at a fulfilling facility.
     */
    public JsonNode qrClaim(Map<String, Object> body) {
        String url = baseUrl + "/v1/intake/qr/claim";
        log.info("OROS: QR claim");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Assign a routing destination to an order.
     */
    public JsonNode routeOrder(String orderId, Map<String, Object> body) {
        String url = baseUrl + "/v1/orders/" + orderId + "/route";
        log.info("OROS: Routing order={} to type={}", orderId, body.get("destinationType"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Search diagnostic orders (order tracking) by client/requester/status/type.
     */
    public JsonNode listOrders(String client, String requester, String status, String type) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/orders");
        if (client != null && !client.isBlank()) builder.queryParam("client", client);
        if (requester != null && !requester.isBlank()) builder.queryParam("requester", requester);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        if (type != null && !type.isBlank()) builder.queryParam("type", type);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * List orders linked to a specific encounter (backs the encounter Orders &amp; Results panel).
     */
    public JsonNode listOrdersByEncounter(String encounterId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/orders")
                .queryParam("encounter", encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Record a specimen collected against a lab order (real specimen lifecycle, not a stub).
     */
    public JsonNode collectSpecimen(String orderId, Map<String, Object> body) {
        String url = baseUrl + "/v1/orders/" + orderId + "/specimens";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Specimens for an order (lab specimen lifecycle view). */
    public JsonNode orderSpecimens(String orderId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/orders/" + orderId + "/specimens", JsonNode.class);
        return extractData(response);
    }

    /** Category-agnostic fulfilment worklist (lab/procedure/assessment). */
    public JsonNode fulfilmentWorklist(String type, String states) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/fulfilment/worklist")
                .queryParam("type", type);
        if (states != null && !states.isBlank()) b.queryParam("states", states);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Drive a guarded fine-grained workflow transition (lab/procedure). */
    public JsonNode workflowTransition(String orderId, String target, String reason) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("target", target);
        body.put("reason", reason);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/orders/" + orderId + "/workflow/transition", body, JsonNode.class);
        return extractData(response);
    }

    /** Schedule a procedure/assessment appointment. */
    public JsonNode workflowSchedule(String orderId, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/orders/" + orderId + "/workflow/schedule",
                body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Drive a specimen lifecycle action (label/dispatch/receive/reject/recollect). */
    public JsonNode specimenAction(String specimenId, String action, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/specimens/" + specimenId + "/" + action,
                body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Read a catalogue segment (services / orderables / specimen-config) — clinician-facing read. */
    public JsonNode catalogueRead(String segment) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/catalogue/" + segment, JsonNode.class);
        return extractData(response);
    }

    /** Curate (upsert) an admin catalogue (service-catalogue / orderable-catalogue / specimen-config). */
    public JsonNode catalogueWrite(String adminPath, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/v1/admin/" + adminPath, HttpMethod.PUT,
                new HttpEntity<>(body != null ? body : Map.of()), JsonNode.class);
        return extractData(response);
    }

    /** Structured laboratory observations for a result (value/unit/reference-range/flags). */
    public JsonNode resultObservations(String resultId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/results/" + resultId + "/observations", JsonNode.class);
        return extractData(response);
    }

    /** All results for an order (patient-file investigations view). */
    public JsonNode orderResults(String orderId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/orders/" + orderId + "/results", JsonNode.class);
        return extractData(response);
    }

    /**
     * Drive a guarded imaging-workflow transition on an order.
     */
    public JsonNode imagingTransition(String orderId, String target, String reason) {
        String url = baseUrl + "/v1/orders/" + orderId + "/imaging/transition";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("target", target);
        if (reason != null) body.put("reason", reason);
        log.info("OROS: Imaging transition order={} -> {}", orderId, target);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Imaging-team worklist filtered by fine-grained imaging state.
     */
    public JsonNode imagingWorklist(String states) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/orders/imaging-worklist");
        if (states != null && !states.isBlank()) builder.queryParam("states", states);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Requester results inbox: orders with results ready for review.
     */
    public JsonNode resultsInbox(String requester) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/results/inbox");
        if (requester != null && !requester.isBlank()) builder.queryParam("requester", requester);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Diagnostic operational reconciliation summary (stuck-order bucket counts + unacked critical).
     */
    public JsonNode reconcileDiagnosticsSummary() {
        String url = baseUrl + "/v1/reconcile/diagnostics/summary";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Author or amend a report for an order ({@code action} = preliminary|final|amend|addendum).
     */
    public JsonNode postReport(String orderId, String action, Map<String, Object> body) {
        String url = baseUrl + "/v1/results/" + orderId + "/" + action;
        log.info("OROS: Report {} for order={}", action, orderId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Release a report version to requesters.
     */
    public JsonNode releaseReport(String resultId, String note) {
        String url = baseUrl + "/v1/results/" + resultId + "/release";
        Map<String, Object> body = note != null ? Map.of("note", note) : Map.of();
        log.info("OROS: Releasing report result={}", resultId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Full report version chain for an order (newest first).
     */
    public JsonNode orderReportVersions(String orderId) {
        String url = baseUrl + "/v1/results/" + orderId + "/versions";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Acknowledge a critical result (closes the critical loop).
     */
    public JsonNode acknowledgeCriticalResult(String resultId, String note) {
        String url = baseUrl + "/v1/results/" + resultId + "/critical/ack";
        Map<String, Object> body = note != null ? Map.of("note", note) : Map.of();
        log.info("OROS: Acknowledging critical result={}", resultId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Unacknowledged critical results requiring follow-up/escalation.
     */
    public JsonNode criticalUnacknowledged() {
        String url = baseUrl + "/v1/reconcile/diagnostics/critical-unacknowledged";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Imaging workload/turnaround distribution by state.
     */
    public JsonNode turnaround() {
        String url = baseUrl + "/v1/metrics/turnaround";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Honest external-integration status for diagnostics adapters.
     */
    public JsonNode integrationStatus() {
        String url = baseUrl + "/v1/integrations/status";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get the orderable catalog, optionally filtered by order type.
     */
    public JsonNode getCatalog(String orderType, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/catalog")
                .queryParam("page", page)
                .queryParam("size", size);
        if (orderType != null && !orderType.isBlank()) {
            builder.queryParam("orderType", orderType);
        }
        log.debug("OROS: Getting catalog orderType={} page={} size={}", orderType, page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get pending hybrid-mode reconciliation entries.
     */
    public JsonNode getReconcilePending(int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/reconcile/pending")
                .queryParam("page", page)
                .queryParam("size", size);
        log.debug("OROS: Getting pending reconciliations page={} size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Match a reconciliation entry to an existing order.
     */
    public JsonNode matchReconciliation(String recId, String orderId) {
        String url = baseUrl + "/v1/reconcile/" + recId + "/match";
        Map<String, Object> body = Map.of("orderId", orderId);
        log.info("OROS: Matching reconciliation recId={} to orderId={}", recId, orderId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Resolve a reconciliation entry with operational notes.
     */
    public JsonNode resolveReconciliation(String recId, String notes) {
        String url = baseUrl + "/v1/reconcile/" + recId + "/resolve";
        Map<String, Object> body = new LinkedHashMap<>();
        if (notes != null) {
            body.put("notes", notes);
        }
        log.info("OROS: Resolving reconciliation recId={}", recId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
