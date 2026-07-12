package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin proxy for the TUSO facility-classification reconciliation surface. Composes nothing and persists
 * nothing — it forwards to TUSO (the facility SoR) and wraps responses in the shell envelope, preserving
 * TUSO's 400/403/404 semantics.
 *
 * <p>All routes sit under {@code /internal/v1/admin/**}, which {@code SecurityConfig} restricts to admin
 * roles; TUSO additionally enforces the registry-admin actor-type gate on mutations.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/facility-classification")
public class AdminFacilityClassificationController {

    private static final Logger log = LoggerFactory.getLogger(AdminFacilityClassificationController.class);

    private final TusoServiceClient tusoClient;

    public AdminFacilityClassificationController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    // ── enrichment runs ──────────────────────────────────────────────────────

    @PostMapping("/enrichment-runs")
    public ResponseEntity<Map<String, Object>> runEnrichment(@RequestBody(required = false) Map<String, Object> body,
                                                             @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                             @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/enrichment-runs", body));
    }

    @GetMapping("/enrichment-runs")
    public ResponseEntity<Map<String, Object>> listRuns(@RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                        @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationGet("/enrichment-runs", null));
    }

    @GetMapping("/enrichment-runs/{runId}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable long runId,
                                                      @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                      @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationGet("/enrichment-runs/" + runId, null));
    }

    // ── profiles ─────────────────────────────────────────────────────────────

    @GetMapping("/profiles")
    public ResponseEntity<Map<String, Object>> listProfiles(@RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String hpaClass,
                                                            @RequestParam(required = false) String canonicalType,
                                                            @RequestParam(required = false) String sourceType,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "50") int size,
                                                            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        StringBuilder q = new StringBuilder("page=").append(page).append("&size=").append(size);
        if (status != null && !status.isBlank()) q.append("&status=").append(status);
        if (hpaClass != null && !hpaClass.isBlank()) q.append("&hpaClass=").append(hpaClass);
        if (canonicalType != null && !canonicalType.isBlank()) q.append("&canonicalType=").append(canonicalType);
        if (sourceType != null && !sourceType.isBlank()) q.append("&sourceType=").append(sourceType);
        return forward(requestId, correlationId, () -> tusoClient.classificationGet("/profiles", q.toString()));
    }

    @GetMapping("/profiles/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                       @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationGet("/profiles/summary", null));
    }

    @GetMapping("/facilities/{facilityId}")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable long facilityId,
                                                          @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                          @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationGet("/facilities/" + facilityId, null));
    }

    @PostMapping("/facilities/{facilityId}/facts")
    public ResponseEntity<Map<String, Object>> supplyFacts(@PathVariable long facilityId, @RequestBody Map<String, Object> body,
                                                           @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                           @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/facilities/" + facilityId + "/facts", body));
    }

    @PostMapping("/facilities/{facilityId}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable long facilityId, @RequestBody(required = false) Map<String, Object> body,
                                                       @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                       @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/facilities/" + facilityId + "/confirm", body));
    }

    @PostMapping("/facilities/{facilityId}/correct")
    public ResponseEntity<Map<String, Object>> correct(@PathVariable long facilityId, @RequestBody Map<String, Object> body,
                                                       @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                       @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/facilities/" + facilityId + "/correct", body));
    }

    @PostMapping("/bulk-confirm")
    public ResponseEntity<Map<String, Object>> bulkConfirm(@RequestBody Map<String, Object> body,
                                                           @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                           @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/bulk-confirm", body));
    }

    // ── unit candidates ──────────────────────────────────────────────────────

    @GetMapping("/facilities/{facilityId}/unit-candidates")
    public ResponseEntity<Map<String, Object>> listCandidates(@PathVariable long facilityId,
                                                              @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                              @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationGet("/facilities/" + facilityId + "/unit-candidates", null));
    }

    @PostMapping("/facilities/{facilityId}/unit-candidates")
    public ResponseEntity<Map<String, Object>> proposeCandidate(@PathVariable long facilityId, @RequestBody Map<String, Object> body,
                                                                @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                                @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/facilities/" + facilityId + "/unit-candidates", body));
    }

    @PostMapping("/unit-candidates/{candidateId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmCandidate(@PathVariable String candidateId, @RequestBody(required = false) Map<String, Object> body,
                                                                @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                                @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/unit-candidates/" + candidateId + "/confirm", body));
    }

    @PostMapping("/unit-candidates/{candidateId}/reject")
    public ResponseEntity<Map<String, Object>> rejectCandidate(@PathVariable String candidateId,
                                                               @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                               @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/unit-candidates/" + candidateId + "/reject", null));
    }

    @PostMapping("/facilities/{facilityId}/unit-candidates/complete-review")
    public ResponseEntity<Map<String, Object>> completeUnitReview(@PathVariable long facilityId,
                                                                  @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
                                                                  @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(requestId, correlationId, () -> tusoClient.classificationPost("/facilities/" + facilityId + "/unit-candidates/complete-review", null));
    }

    // ── plumbing ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> forward(String requestId, String correlationId,
                                                        java.util.function.Supplier<JsonNode> call) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            // Preserve TUSO's status (400 validation / 403 authz / 404 not-found).
            return ResponseEntity.status(e.getStatusCode())
                    .body(error(requestId, correlationId, "CLASSIFICATION_UPSTREAM", e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("Facility classification passthrough failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(error(requestId, correlationId, "CLASSIFICATION_UNAVAILABLE",
                            "Unable to reach the facility classification service"));
        }
    }

    private Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }

    private Map<String, Object> error(String requestId, String correlationId, String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        m.put("error", err);
        m.put("meta", meta(requestId, correlationId));
        return m;
    }
}
