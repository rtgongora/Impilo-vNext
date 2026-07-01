package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

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

    /**
     * Row-level view. Row-level import staging is NOT persisted per run yet, so this route reports that
     * honestly rather than fabricating rows. Run-level totals live on the run detail; the excluded/review
     * source datasets remain the authoritative row-level record until staging lands.
     */
    @GetMapping("/facility-import-runs/{runId}/rows")
    public ResponseEntity<Map<String, Object>> getRunRows(
            @PathVariable long runId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contract", "facility-import-run-rows-v1");
        data.put("runId", runId);
        data.put("rowLevelStaging", "NOT_PERSISTED");
        data.put("rows", java.util.List.of());
        data.put("message", "Row-level import staging is not persisted per run yet. See run breakdown "
                + "totals and the source review datasets; row-level staging is the next backend slice.");
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    /**
     * Review buckets for a run, reshaped from the run's real breakdown totals. Per-row review detail
     * requires row-level staging (not yet persisted) — flagged via {@code rowLevelDetailAvailable}.
     */
    @GetMapping("/facility-import-runs/{runId}/review")
    public ResponseEntity<Map<String, Object>> getRunReview(
            @PathVariable long runId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        JsonNode run;
        try {
            run = tusoClient.getFacilityImportRun(runId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "IMPORT_RUN_UNAVAILABLE",
                    "Unable to load facility import run from TUSO");
        }
        if (run == null || run.isNull()) {
            return notFound(requestId, correlationId, "IMPORT_RUN_NOT_FOUND", "Import run not found: " + runId);
        }
        JsonNode totals = run.path("totals");
        Map<String, Object> buckets = new LinkedHashMap<>();
        buckets.put("missing_facility_code", totals.path("missingFacilityCode").asLong(0));
        buckets.put("duplicate_facility_code", totals.path("duplicateFacilityCode").asLong(0));
        buckets.put("duplicate_facility_name", totals.path("duplicateFacilityName").asLong(0));
        buckets.put("acceptable_missing", totals.path("acceptableMissing").asLong(0));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contract", "facility-import-run-review-v1");
        data.put("runId", runId);
        data.put("buckets", buckets);
        data.put("rowLevelDetailAvailable", false);
        data.put("message", "Review counts are real; per-row review detail requires row-level staging "
                + "(next backend slice). Excluded/review source datasets remain authoritative for rows.");
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
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
