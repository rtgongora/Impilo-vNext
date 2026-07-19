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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin proxy for the facility master-absorption read surfaces. Composes nothing and persists nothing —
 * it forwards to TUSO (the facility SoR) and wraps the response in the shell envelope.
 *
 * <p><b>Policy.</b> All routes sit under {@code /internal/v1/admin/**}, which {@code SecurityConfig}
 * restricts to admin roles (FACILITY_ADMIN / SYSTEM_ADMIN / DEVELOPER / SUPER_ADMIN). Import/run data is
 * therefore not exposed to ordinary facility users. (The spec's suggested unqualified paths are namespaced
 * under {@code /admin} to inherit this RBAC seam rather than inventing a new one.)</p>
 */
@RestController
@RequestMapping("/internal/v1/admin")
public class AdminFacilityImportController {

    private static final Logger log = LoggerFactory.getLogger(AdminFacilityImportController.class);

    private final TusoServiceClient tusoClient;

    public AdminFacilityImportController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @GetMapping("/facility-import-runs")
    public ResponseEntity<Map<String, Object>> listRuns(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.listFacilityImportRuns();
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_RUNS_UNAVAILABLE",
                    "Unable to load facility import runs from TUSO");
        }
    }

    // ── HPA national facility enrichment import ─────────────────────────────

    @GetMapping("/hpa-import-runs")
    public ResponseEntity<Map<String, Object>> listHpaRuns(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.hpaImportRuns();
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "HPA_IMPORT_RUNS_UNAVAILABLE",
                    "Unable to load HPA import runs from TUSO");
        }
    }

    @GetMapping("/hpa-import-runs/{runId}/outcomes")
    public ResponseEntity<Map<String, Object>> hpaOutcomes(
            @PathVariable long runId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.hpaImportOutcomes(runId);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "HPA_IMPORT_OUTCOMES_UNAVAILABLE",
                    "Unable to load HPA import outcomes from TUSO");
        }
    }

    @GetMapping("/hpa-import-runs/{runId}/review-queue")
    public ResponseEntity<Map<String, Object>> hpaReviewQueue(
            @PathVariable long runId,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.hpaImportReviewQueue(runId, outcome);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "HPA_IMPORT_REVIEW_UNAVAILABLE",
                    "Unable to load HPA import review queue from TUSO");
        }
    }

    @GetMapping("/facility-import-runs/{runId}")
    public ResponseEntity<Map<String, Object>> getRun(
            @PathVariable long runId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        JsonNode data;
        try {
            data = tusoClient.getFacilityImportRun(runId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_RUN_UNAVAILABLE",
                    "Unable to load facility import run from TUSO");
        }
        if (data == null || data.isNull()) {
            return notFound(requestId, correlationId, "IMPORT_RUN_NOT_FOUND", "Import run not found: " + runId);
        }
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    /** Real persisted row-level outcomes for a run (filters + pagination proxied to TUSO). */
    @GetMapping("/facility-import-runs/{runId}/rows")
    public ResponseEntity<Map<String, Object>> getRunRows(
            @PathVariable long runId,
            @RequestParam Map<String, String> params,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.getFacilityImportRunRows(runId, buildQuery(params));
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_ROWS_UNAVAILABLE",
                    "Unable to load facility import rows from TUSO");
        }
    }

    /** Real review buckets (counts + previews) built from persisted rows. */
    @GetMapping("/facility-import-runs/{runId}/review")
    public ResponseEntity<Map<String, Object>> getRunReview(
            @PathVariable long runId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.getFacilityImportRunReview(runId);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_REVIEW_UNAVAILABLE",
                    "Unable to load facility import review from TUSO");
        }
    }

    /** Duplicate rows grouped for review. */
    @GetMapping("/facility-import-runs/{runId}/duplicates")
    public ResponseEntity<Map<String, Object>> getRunDuplicates(
            @PathVariable long runId,
            @RequestParam(value = "type", required = false) String type,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.getFacilityImportRunDuplicates(runId, type);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_DUPLICATES_UNAVAILABLE",
                    "Unable to load facility import duplicates from TUSO");
        }
    }

    /** Rows excluded for missing facility code. */
    @GetMapping("/facility-import-runs/{runId}/missing-code")
    public ResponseEntity<Map<String, Object>> getRunMissingCode(
            @PathVariable long runId,
            @RequestParam Map<String, String> params,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.getFacilityImportRunMissingCode(runId, buildQuery(params));
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_MISSING_CODE_UNAVAILABLE",
                    "Unable to load missing-code rows from TUSO");
        }
    }

    /** Import-eligible rows carrying acceptable-missing fields. */
    @GetMapping("/facility-import-runs/{runId}/acceptable-missing")
    public ResponseEntity<Map<String, Object>> getRunAcceptableMissing(
            @PathVariable long runId,
            @RequestParam Map<String, String> params,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.getFacilityImportRunAcceptableMissing(runId, buildQuery(params));
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_ACCEPTABLE_MISSING_UNAVAILABLE",
                    "Unable to load acceptable-missing rows from TUSO");
        }
    }

    /** Forward only the recognised row filters/pagination params (drop unknowns) as a query string. */
    private static String buildQuery(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        java.util.List<String> allowed = java.util.List.of("status", "outcome", "duplicateType",
                "province", "district", "facilityCode", "facilityName", "hasAcceptableMissingFields",
                "page", "size");
        StringBuilder sb = new StringBuilder();
        for (String key : allowed) {
            String v = params.get(key);
            if (v != null && !v.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    @GetMapping("/facilities/{facilityId}/import-provenance")
    public ResponseEntity<Map<String, Object>> importProvenance(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        JsonNode data;
        try {
            data = tusoClient.getFacilityImportProvenance(facilityId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_PROVENANCE_UNAVAILABLE",
                    "Unable to load facility import provenance from TUSO");
        }
        if (data == null || data.isNull()) {
            return notFound(requestId, correlationId, "FACILITY_NOT_FOUND", "Facility not found: " + facilityId);
        }
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    /** Missing-field / configuration checklist slice of the facility import provenance. */
    @GetMapping("/facilities/{facilityId}/missing-field-checklist")
    public ResponseEntity<Map<String, Object>> missingFieldChecklist(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        JsonNode prov;
        try {
            prov = tusoClient.getFacilityImportProvenance(facilityId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_PROVENANCE_UNAVAILABLE",
                    "Unable to load facility import provenance from TUSO");
        }
        if (prov == null || prov.isNull()) {
            return notFound(requestId, correlationId, "FACILITY_NOT_FOUND", "Facility not found: " + facilityId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contract", "facility-missing-field-checklist-v1");
        data.put("facilityId", facilityId);
        data.put("acceptableMissing", prov.path("acceptableMissing"));
        data.put("checklist", prov.path("checklist"));
        data.put("downstreamMaterialisationStatus", prov.path("downstreamMaterialisationStatus").asText(null));
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    // ---- Review-decision mutations (RBAC-protected admin namespace; business rules live in TUSO) ----

    @PostMapping("/facility-import-runs/{runId}/rows/{rowId}/supply-code")
    public ResponseEntity<Map<String, Object>> supplyCode(
            @PathVariable long runId, @PathVariable long rowId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "supply-code", body, requestId, correlationId);
    }

    @PostMapping("/facility-import-runs/{runId}/rows/{rowId}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable long runId, @PathVariable long rowId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "reject", body, requestId, correlationId);
    }

    @PostMapping("/facility-import-runs/{runId}/rows/{rowId}/skip")
    public ResponseEntity<Map<String, Object>> skip(
            @PathVariable long runId, @PathVariable long rowId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "skip", body, requestId, correlationId);
    }

    @PostMapping("/facility-import-runs/{runId}/rows/{rowId}/match-existing")
    public ResponseEntity<Map<String, Object>> matchExisting(
            @PathVariable long runId, @PathVariable long rowId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "match-existing", body, requestId, correlationId);
    }

    @PostMapping("/facility-import-runs/{runId}/rows/{rowId}/resolve-distinct")
    public ResponseEntity<Map<String, Object>> resolveDistinct(
            @PathVariable long runId, @PathVariable long rowId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "resolve-distinct", body, requestId, correlationId);
    }

    @PatchMapping("/facility-import-runs/{runId}/rows/{rowId}/canonical-values")
    public ResponseEntity<Map<String, Object>> updateCanonical(
            @PathVariable long runId, @PathVariable long rowId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "canonical-values", body, requestId, correlationId);
    }

    @PostMapping("/facility-import-runs/{runId}/rows/{rowId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable long runId, @PathVariable long rowId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRowAction(runId, rowId, "approve", null, requestId, correlationId);
    }

    @PostMapping("/facility-import-runs/{runId}/apply-approved")
    public ResponseEntity<Map<String, Object>> applyApproved(
            @PathVariable long runId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = tusoClient.postImportRunAction(runId, "apply-approved", body);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "APPLY_APPROVED_FAILED",
                    "Unable to apply approved rows via TUSO");
        }
    }

    private ResponseEntity<Map<String, Object>> proxyRowAction(
            long runId, long rowId, String action, Map<String, Object> body,
            String requestId, String correlationId) {
        try {
            JsonNode data = tusoClient.postImportRowAction(runId, rowId, action, body);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_ROW_ACTION_FAILED",
                    "Unable to apply review decision via TUSO");
        }
    }

    /** Propagate a downstream 4xx (validation/conflict) with its status + message, not a 502. */
    private ResponseEntity<Map<String, Object>> passthrough(
            HttpStatusCodeException e, String requestId, String correlationId) {
        String message = e.getResponseBodyAsString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", Map.of("code", "IMPORT_REVIEW_REJECTED",
                        "message", message == null || message.isBlank() ? e.getStatusText() : message),
                "meta", meta(requestId, correlationId)));
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    private ResponseEntity<Map<String, Object>> badGateway(String requestId, String correlationId,
                                                           String code, String message) {
        log.warn("TUSO admin proxy failure [{}]: {}", code, message);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> notFound(String requestId, String correlationId,
                                                         String code, String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }
}
