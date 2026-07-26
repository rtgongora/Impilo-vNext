package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.StaffingReadService;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Staffing read-models and the shift-swap workflow (V009).
 *
 * <p>Deliberately a separate controller from {@link VashandiRosterController}: rosters and shifts
 * are being repointed by another lane, and two sessions editing one controller is how a merge
 * silently loses an endpoint.</p>
 *
 * <p>These three surfaces are what experience-bff has been asking tuso for
 * ({@code /v1/staffing/roster-week}, {@code /v1/staffing/on-call}, {@code /v1/staffing/on-call/swaps}),
 * a path no service serves — so the roster screen, on-call screen and control tower rendered empty
 * while every layer reported success.</p>
 */
@RestController
@RequestMapping("/v1/internal/vashandi/staffing")
public class VashandiStaffingController {

    private final StaffingReadService staffingService;

    public VashandiStaffingController(StaffingReadService staffingService) {
        this.staffingService = staffingService;
    }

    @GetMapping("/roster-week")
    public ResponseEntity<Map<String, Object>> rosterWeek(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(name = "week_start") String weekStart) {
        return ResponseEntity.ok(Map.of(
                "data", staffingService.rosterWeek(tenantId, facilityId, LocalDate.parse(weekStart))));
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> onCall(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(name = "week_start") String weekStart) {
        return ResponseEntity.ok(Map.of(
                "data", staffingService.onCallWeek(tenantId, facilityId, LocalDate.parse(weekStart))));
    }

    @GetMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> listSwaps(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(Map.of(
                "data", staffingService.listSwaps(tenantId, facilityId, status)));
    }

    @PostMapping("/on-call/swaps")
    public ResponseEntity<Map<String, Object>> createSwap(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestBody Map<String, Object> body) throws Exception {
        UUID requestingShiftId = uuid(body, "requestingShiftId", "requesting_shift_id");
        UUID requestedProfileId = uuid(body, "requestedProfileId", "requested_profile_id");
        if (requestingShiftId == null || requestedProfileId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "SWAP_INVALID",
                    "message", "requestingShiftId and requestedProfileId are required — a swap is "
                               + "raised against a rostered shift, not a typed name")));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data",
                staffingService.createSwap(tenantId, requestingShiftId, requestedProfileId,
                        uuid(body, "offeredShiftId", "offered_shift_id"),
                        str(body, "reason"), actorId)));
    }

    @PatchMapping("/on-call/swaps/{id}")
    public ResponseEntity<Map<String, Object>> decideSwap(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) throws Exception {
        return ResponseEntity.ok(Map.of("data", staffingService.decideSwap(
                tenantId, id, str(body, "status"), actorId, str(body, "note"))));
    }

    // ── failure mapping ─────────────────────────────────────────────────────
    // The refusals here carry the reason a rota manager needs (already-open request, swap with
    // oneself, missing decider). Surfaced as 500s those explanations are discarded and the
    // operator retries the same thing.

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onInvalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", Map.of(
                "code", "SWAP_INVALID", "message", String.valueOf(ex.getMessage()))));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", Map.of(
                "code", "SWAP_CONFLICT", "message", String.valueOf(ex.getMessage()))));
    }

    private static UUID uuid(Map<String, Object> body, String... keys) {
        String v = str(body, keys);
        return v == null ? null : UUID.fromString(v);
    }

    private static String str(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
