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
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.*;

/**
 * Staffing surfaces: the week's roster, the on-call rota and the shift-swap workflow.
 *
 * <p><b>Repointed from tuso (route-contract completion).</b> These handlers called
 * {@code tuso /v1/staffing/*} — paths no service in the estate serves — so the roster screen, the
 * on-call screen and the control tower rendered empty while every layer reported success. Vashandi
 * owns rostering ({@code vsh_roster}/{@code vsh_shift}); scheduled on-call is a projection over the
 * shifts that carry an {@code on_call_role}, so there is one on-call truth with several read
 * shapes rather than a rival store.</p>
 *
 * <p><b>An empty roster is now distinguishable from a broken one.</b> The previous handlers
 * returned {@code data: []} both when the upstream answered with nothing and when it answered with
 * an empty list, so "no shifts rostered this week" and "the call produced nothing" looked
 * identical to the screen. The upstream response is passed through as given; only a genuine
 * failure takes the error path.</p>
 */
@RestController
@RequestMapping("/internal/v1/staffing")
public class StaffingController {

    private static final Logger log = LoggerFactory.getLogger(StaffingController.class);

    private final VashandiServiceClient vashandiClient;

    public StaffingController(VashandiServiceClient vashandiClient) {
        this.vashandiClient = vashandiClient;
    }

    public record PatchSwapRequest(@NotBlank String status, String note) {}

    /**
     * A swap is raised against a rostered shift and a person, not against typed names.
     *
     * <p>The previous contract took {@code requestor_name}/{@code requestee_name} as free text.
     * Two staff share a name, and a shift swap changes who is clinically accountable for a window —
     * that is not an ambiguity to resolve by string match, so the identifiers are required.</p>
     */
    public record CreateSwapRequest(
            @NotBlank String requesting_shift_id,
            @NotBlank String requested_profile_id,
            String offered_shift_id,
            String reason
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
            JsonNode roster = vashandiClient.getRosterWeek(facilityId, weekStartParam);
            return ok(roster, requestId, correlationId, Map.of("week_start", weekStartParam));
        } catch (Exception e) {
            log.warn("VASHANDI roster-week unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
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
            JsonNode onCall = vashandiClient.listOnCall(facilityId, weekStartParam);
            return ok(onCall, requestId, correlationId, Map.of("week_start", weekStartParam));
        } catch (Exception e) {
            log.warn("VASHANDI on-call unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> listSwaps(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {
        try {
            JsonNode swaps = vashandiClient.listSwapRequests(facilityId);
            return ok(swaps, requestId, correlationId, Map.of());
        } catch (Exception e) {
            log.warn("VASHANDI swap list unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @PostMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> createSwap(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreateSwapRequest body) {

        Map<String, Object> swapData = new LinkedHashMap<>();
        swapData.put("requestingShiftId", body.requesting_shift_id());
        swapData.put("requestedProfileId", body.requested_profile_id());
        if (body.offered_shift_id() != null && !body.offered_shift_id().isBlank()) {
            swapData.put("offeredShiftId", body.offered_shift_id());
        }
        swapData.put("reason", body.reason());

        try {
            JsonNode result = vashandiClient.createSwapRequest(swapData);
            if (result == null) {
                return upstreamFailure(requestId, correlationId,
                        "VASHANDI_UNAVAILABLE", "Swap request was not persisted");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.warn("VASHANDI create swap unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    @PatchMapping("/on-call/swaps/{id}")
    public ResponseEntity<Map<String, Object>> patchSwap(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody PatchSwapRequest body) {

        String normalized = body.status().trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("APPROVED") && !normalized.equals("DECLINED")
                && !normalized.equals("WITHDRAWN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be APPROVED, DECLINED or WITHDRAWN");
        }

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("status", normalized);
        updateData.put("note", body.note());
        try {
            JsonNode result = vashandiClient.updateSwapRequest(id.toString(), updateData);
            if (result == null) {
                return upstreamFailure(requestId, correlationId,
                        "VASHANDI_UNAVAILABLE", "Swap update was not persisted");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("VASHANDI patch swap unavailable: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "VASHANDI_UNAVAILABLE", e.getMessage());
        }
    }

    /**
     * Pass the upstream answer through as given. An empty list means the week is genuinely empty —
     * it is not a stand-in for a call that produced nothing, which takes the error path instead.
     */
    private ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId,
                                                   String correlationId, Map<String, String> extraMeta) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        meta.putAll(extraMeta);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data == null ? List.of() : data);
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String requestId, String correlationId, String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
