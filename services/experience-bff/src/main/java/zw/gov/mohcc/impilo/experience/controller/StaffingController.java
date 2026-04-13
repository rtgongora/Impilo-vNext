package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;

/**
 * Staffing surfaces: roster from recorded shifts, on-call assignments and swap workflow.
 * Delegates to TusoServiceClient.
 */
@RestController
@RequestMapping("/internal/v1/staffing")
public class StaffingController {

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

        JsonNode roster = tusoClient.getRosterWeek(facilityId, weekStartParam, workspaceId);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", roster);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "week_start", weekStartParam));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> listOnCall(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(name = "week_start") String weekStartParam) {

        JsonNode onCall = tusoClient.listOnCall(facilityId, weekStartParam);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", onCall);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "week_start", weekStartParam));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> listSwaps(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        JsonNode swaps = tusoClient.listSwapRequests(facilityId);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, facilityId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", swaps);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
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

        JsonNode result = tusoClient.createSwapRequest(swapData);

        // jdbcTemplate.update("""
        //     INSERT INTO on_call_swap_requests (...) VALUES (?)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(201).body(response);
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
        JsonNode result = tusoClient.updateSwapRequest(id.toString(), updateData);

        // jdbcTemplate.update("""
        //     UPDATE on_call_swap_requests SET status = ?, updated_at = NOW()
        //     WHERE id = ?::uuid AND tenant_id = ?
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
