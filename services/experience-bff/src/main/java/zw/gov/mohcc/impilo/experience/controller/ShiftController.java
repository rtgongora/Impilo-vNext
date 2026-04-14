package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Shift management endpoints.
 * Falls back to local shift state when TUSO is unavailable.
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
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", shift);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.debug("TUSO unavailable for current shift: {}", e.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", null);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
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
            log.info("TUSO unavailable — creating local shift fallback: {}", e.getMessage());
        }

        // Fallback: return a local shift
        String shiftId = UUID.randomUUID().toString();
        String now = OffsetDateTime.now().toString();

        // Return camelCase to match what the UI expects
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("status", "ACTIVE");
        attrs.put("facilityId", facilityId);
        attrs.put("workspaceId", workspaceId);
        attrs.put("userId", userId);
        attrs.put("startedAt", now);
        attrs.put("tenantId", tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", shiftId, "type", "shift", "attributes", attrs));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
            log.info("TUSO unavailable — returning local shift end: {}", e.getMessage());
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("status", "ENDED");
        attrs.put("endedAt", OffsetDateTime.now().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "type", "shift", "attributes", attrs));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }
}
