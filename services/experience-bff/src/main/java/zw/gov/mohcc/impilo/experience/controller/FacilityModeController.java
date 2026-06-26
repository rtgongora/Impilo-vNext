package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.queue.QueueStatsAggregator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Composes the Facility Mode cockpit context for the shell.
 *
 * <p>TUSO is the single producer of the C4 {@code FacilityModeContext} read-model
 * (facility node + setup state + persisted ops). This BFF controller is a stateless
 * composer: it fetches the TUSO context and, when a live PCT facility UUID is
 * available, overlays the live queue depth / open-encounter counts. It persists
 * nothing (the BFF datasource is dead).</p>
 *
 * <p>Authz via TSHEPO ext_authz (existing path); policy {@code FACILITY-MODE-ENTER}
 * (spec-only, track P) gates entry. Cross-tenant visibility is enforced inside TUSO
 * via {@code AggregateVisibilityGuard} and reflected in the returned
 * {@code visibility} field.</p>
 */
@RestController
@RequestMapping("/internal/v1/facility-mode")
public class FacilityModeController {

    private static final Logger log = LoggerFactory.getLogger(FacilityModeController.class);

    private final TusoServiceClient tusoClient;
    private final PctServiceClient pctClient;

    public FacilityModeController(TusoServiceClient tusoClient, PctServiceClient pctClient) {
        this.tusoClient = tusoClient;
        this.pctClient = pctClient;
    }

    /**
     * Facility Mode cockpit context.
     *
     * @param facilityId numeric TUSO facility id (the cockpit anchor)
     * @param pctFacilityId optional live PCT facility UUID for queue overlay
     */
    @GetMapping("/{facilityId}/context")
    public ResponseEntity<Map<String, Object>> getContext(
            @PathVariable long facilityId,
            @RequestParam(value = "pctFacilityId", required = false) String pctFacilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode context;
        try {
            context = tusoClient.getFacilityModeContext(facilityId);
        } catch (Exception e) {
            log.warn("TUSO facility-mode context unavailable for facility {}: {}", facilityId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "FACILITY_MODE_CONTEXT_UNAVAILABLE",
                            "message", "Unable to load facility-mode context from TUSO"),
                    "meta", meta(requestId, correlationId)));
        }
        if (context == null || context.isNull()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "FACILITY_NOT_FOUND",
                            "message", "Facility not found: " + facilityId),
                    "meta", meta(requestId, correlationId)));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contract", "facility-mode-context-v1");
        data.put("source", "TUSO");
        data.put("context", context);

        // Overlay live PCT queue depth / open encounters when a UUID facility ref is given
        // and the context is NOT aggregate-only (no per-facility row detail under aggregate).
        boolean aggregateOnly = "AGGREGATE_ONLY".equals(context.path("visibility").asText(null));
        if (pctFacilityId != null && !pctFacilityId.isBlank() && !aggregateOnly) {
            try {
                JsonNode queues = pctClient.listQueues(UUID.fromString(pctFacilityId), null);
                Map<String, Object> liveOps = QueueStatsAggregator.fromPctQueueList(queues);
                data.put("liveOps", liveOps);
                data.put("liveOpsSource", "PCT");
            } catch (Exception e) {
                log.debug("PCT queue overlay unavailable for facility {}: {}", pctFacilityId, e.getMessage());
                data.put("liveOpsSource", "UNAVAILABLE");
            }
        }

        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    /** Setup-wizard state passthrough (TUSO SoR). */
    @GetMapping("/{facilityId}/setup")
    public ResponseEntity<Map<String, Object>> getSetup(
            @PathVariable long facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode state = tusoClient.getFacilitySetupState(facilityId);
            return ResponseEntity.ok(Map.of("data", state, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "FACILITY_SETUP_UNAVAILABLE",
                    "Unable to load facility setup state from TUSO");
        }
    }

    /** Advance a setup-wizard step (TUSO SoR). */
    @PostMapping("/{facilityId}/setup/steps")
    public ResponseEntity<Map<String, Object>> updateSetupStep(
            @PathVariable long facilityId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode state = tusoClient.updateFacilitySetupStep(facilityId, body);
            return ResponseEntity.ok(Map.of("data", state, "meta", meta(requestId, correlationId)));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // surface TUSO's honest go-live / validation failures verbatim
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "error", Map.of("code", "FACILITY_SETUP_STEP_REJECTED",
                            "message", e.getResponseBodyAsString()),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "FACILITY_SETUP_UNAVAILABLE",
                    "Unable to update facility setup step in TUSO");
        }
    }

    /** List facility units (departments) — TUSO SoR. */
    @GetMapping("/{facilityId}/units")
    public ResponseEntity<Map<String, Object>> listUnits(
            @PathVariable long facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode units = tusoClient.listFacilityUnits(facilityId);
            return ResponseEntity.ok(Map.of("data", units, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "FACILITY_UNITS_UNAVAILABLE",
                    "Unable to load facility units from TUSO");
        }
    }

    /** Create a facility unit (department) — TUSO SoR. */
    @PostMapping("/{facilityId}/units")
    public ResponseEntity<Map<String, Object>> createUnit(
            @PathVariable long facilityId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode unit = tusoClient.createFacilityUnit(facilityId, body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", unit, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "FACILITY_UNIT_CREATE_FAILED",
                    "Unable to create facility unit in TUSO");
        }
    }

    /** List facility service points — TUSO SoR. */
    @GetMapping("/{facilityId}/service-points")
    public ResponseEntity<Map<String, Object>> listServicePoints(
            @PathVariable long facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode sps = tusoClient.listServicePoints(facilityId);
            return ResponseEntity.ok(Map.of("data", sps, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "SERVICE_POINTS_UNAVAILABLE",
                    "Unable to load service points from TUSO");
        }
    }

    /** Create a facility service point — TUSO SoR. */
    @PostMapping("/{facilityId}/service-points")
    public ResponseEntity<Map<String, Object>> createServicePoint(
            @PathVariable long facilityId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode sp = tusoClient.createServicePoint(facilityId, body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", sp, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "SERVICE_POINT_CREATE_FAILED",
                    "Unable to create service point in TUSO");
        }
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    private ResponseEntity<Map<String, Object>> badGateway(String requestId, String correlationId,
                                                           String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }
}
