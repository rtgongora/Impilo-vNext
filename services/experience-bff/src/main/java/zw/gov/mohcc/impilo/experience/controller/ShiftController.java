package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.support.JsonApiRows;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Shift management endpoints. TUSO is the system of record for duty state.
 *
 * <p>These used to fall back to "local shift state" when TUSO was unavailable, but the BFF is
 * stateless — nothing was recorded. A fabricated shift makes someone appear on duty when no
 * system knows they are, and duty state feeds work-context binding at the PDP. Failures are
 * reported as 502 instead.</p>
 */
@RestController
@RequestMapping("/internal/v1/shifts")
public class ShiftController {

    private static final Logger log = LoggerFactory.getLogger(ShiftController.class);

    private final TusoServiceClient tusoClient;

    public ShiftController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "user_id") String userId) {

        try {
            var shift = tusoClient.getCurrentShift(userId);
            // useShifts declares attributes on the current shift; tuso returns its own entity.
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", JsonApiRows.row(shift, "shift", "id", "shiftId"));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // A null shift is exactly what "this person is not on duty" looks like, and duty state
            // feeds work-context binding — the difference between not-on-shift and not-checked.
            log.error("TUSO getCurrentShift failed for user={}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "current_shift_unavailable",
                    "message", "The current shift could not be retrieved. Do not treat this as being off duty.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        // Accept both camelCase and snake_case field names from the UI
        String facilityId = strVal(body, "facilityId", "facility_id");
        String workspaceId = strVal(body, "workspaceId", "workspace_id");
        String userId = strVal(body, "userId", "user_id");
        if (userId == null || userId.isBlank()) userId = actorId;
        if (userId == null || userId.isBlank()) userId = "anonymous";

        if (facilityId == null || facilityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "facilityId is required")));
        }

        // Try TUSO first
        try {
            Map<String, Object> shiftData = new LinkedHashMap<>();
            shiftData.put("facility_id", facilityId);
            shiftData.put("workspace_id", workspaceId);
            shiftData.put("user_id", userId);
            shiftData.put("tenant_id", tenantId);

            var result = tusoClient.startShift(shiftData);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            // The "local shift fallback" minted a random shift id and reported status ACTIVE for
            // a shift TUSO never opened. The BFF is stateless, so that id exists nowhere: the
            // person appears on duty, and the later handover or end against that id cannot
            // resolve it. Duty state is an authorization input, not a UI convenience.
            log.error("TUSO startShift failed for user={} facility={}: {}",
                    userId, facilityId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "shift_not_started",
                    "message", "The shift could not be started. You are not on duty.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/handover")
    public ResponseEntity<Map<String, Object>> handoverShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        String shiftId = strVal(body, "shiftId", "shift_id");
        String notes = strVal(body, "notes", "handoverNotes", "handover_notes");
        if (shiftId == null || shiftId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "shiftId is required")));
        }

        try {
            Map<String, Object> endData = new LinkedHashMap<>();
            if (notes != null && !notes.isBlank()) {
                endData.put("handover_notes", notes);
            }
            var result = tusoClient.endShift(shiftId, endData);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result != null ? result : Map.of("id", shiftId, "status", "ENDED"));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // "Recording local shift handover" recorded nothing — the BFF is stateless. It
            // returned status HANDED_OVER with an endedAt timestamp for a handover TUSO never
            // saw, so the outgoing nurse believes responsibility has transferred and the incoming
            // one has no record that it did.
            log.error("TUSO shift handover failed for shift={}: {}", shiftId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "shift_handover_not_recorded",
                    "message", "The handover could not be recorded. You are still responsible for "
                               + "this shift.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<Map<String, Object>> endShift(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        try {
            Map<String, Object> endData = new LinkedHashMap<>();
            if (body != null && body.get("handoverNotes") != null) {
                endData.put("handover_notes", body.get("handoverNotes").toString());
            }
            var result = tusoClient.endShift(id, endData);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // As with handover: a "local shift end" is not recorded anywhere, but the response
            // reported status ENDED with a timestamp.
            log.error("TUSO endShift failed for shift={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "shift_end_not_recorded",
                    "message", "The shift could not be ended. It is still open.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }
}
