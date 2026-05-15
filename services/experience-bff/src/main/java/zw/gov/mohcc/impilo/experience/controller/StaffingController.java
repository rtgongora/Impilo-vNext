package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;

/**
 * Staffing surfaces: roster from recorded shifts, on-call assignments and swap workflow.
 * Delegates to TUSO and fails closed on upstream dependency errors.
 */
@RestController
@RequestMapping("/internal/v1/staffing")
public class StaffingController {

    private static final Logger log = LoggerFactory.getLogger(StaffingController.class);

    private final TusoServiceClient tusoClient;

    public StaffingController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    public record PatchSwapRequest(@NotBlank String status) {}

    public record CreateSwapRequest(
            @NotBlank String facility_id,
            @NotBlank String requestor_name,
            @NotBlank String requestee_name,
            @NotBlank String original_date,
            @NotBlank String swap_date,
            String specialty
    ) {}

    @GetMapping("/roster-week")
    public ResponseEntity<Map<String, Object>> rosterWeek(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam,
            @RequestParam(name = "workspace_id", required = false) String workspaceId) {

        try {
            JsonNode roster = tusoClient.getRosterWeek(facilityId, weekStartParam, workspaceId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", roster != null ? roster : List.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                    "week_start", weekStartParam));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("TUSO roster-week unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> listOnCall(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam) {

        try {
            JsonNode onCall = tusoClient.listOnCall(facilityId, weekStartParam);
            if (onCall != null && onCall.isArray() && onCall.size() > 0) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", onCall);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "week_start", weekStartParam));
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.warn("TUSO on-call unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "week_start", weekStartParam)));
    }

    @GetMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> listSwaps(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        try {
            JsonNode swaps = tusoClient.listSwapRequests(facilityId);
            if (swaps != null && swaps.isArray() && swaps.size() > 0) {
                return ResponseEntity.ok(Map.of(
                        "data", swaps,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.warn("TUSO swap list unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> createSwap(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreateSwapRequest body) {

        Map<String, Object> swapData = new LinkedHashMap<>();
        swapData.put("facility_id", body.facility_id());
        swapData.put("requestor_name", body.requestor_name());
        swapData.put("requestee_name", body.requestee_name());
        swapData.put("original_date", body.original_date());
        swapData.put("swap_date", body.swap_date());
        swapData.put("specialty", body.specialty());
        swapData.put("tenant_id", tenantId);

        try {
            JsonNode result = tusoClient.createSwapRequest(swapData);
            if (result != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", result);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.status(201).body(response);
            }
        } catch (Exception e) {
            log.warn("TUSO create swap unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
        return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", "Swap request was not persisted");
    }

    @PatchMapping("/on-call/swaps/{id}")
    public ResponseEntity<Map<String, Object>> patchSwap(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody PatchSwapRequest body) {

        String normalized = body.status().trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("APPROVED") && !normalized.equals("DECLINED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be APPROVED or DECLINED");
        }

        Map<String, Object> updateData = Map.of("status", normalized);
        try {
            JsonNode result = tusoClient.updateSwapRequest(id.toString(), updateData);
            if (result != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", result);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.warn("TUSO patch swap unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
        return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", "Swap update was not persisted");
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String requestId, String correlationId, String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
