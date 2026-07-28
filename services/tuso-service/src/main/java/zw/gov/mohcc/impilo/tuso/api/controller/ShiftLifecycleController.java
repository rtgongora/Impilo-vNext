package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.ShiftResponse;
import zw.gov.mohcc.impilo.tuso.core.WorkspaceService;

import java.util.UUID;

/**
 * Shift lifecycle beyond the facility-scoped start.
 *
 * <p>Starting a shift is facility-scoped and lives on {@link ShiftController}. Asking whether a
 * provider is currently on one, and ending it, are not — you end the shift you are on, wherever it
 * was started.
 *
 * <p>These endpoints existed on the caller's side and nowhere else: the BFF's TusoServiceClient
 * called {@code /v1/shifts/current} and {@code /v1/shifts/{id}/end}, which no service served, so
 * every attempt to check duty state or hand over failed. Shift state is an authorization input —
 * duty is one of the axes an access decision reads — so a clinician's duty state could never be
 * established at all. The records were there the whole time in {@code tuso.shift}; only the reads
 * were missing.
 */
@RestController
@RequestMapping("/v1/internal/shifts")
public class ShiftLifecycleController {

    private static final Logger log = LoggerFactory.getLogger(ShiftLifecycleController.class);

    private final WorkspaceService workspaceService;

    public ShiftLifecycleController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * The provider's active shift.
     *
     * <p>404 when they are not on one. That is a real answer — "not on shift" — and the caller must
     * be able to tell it apart from a failed lookup, because one denies a duty-gated action and the
     * other means we could not decide.
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<ShiftResponse>> getCurrentShift(
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "providerId", required = false) String providerId) {

        TrustContext ctx = TrustContextHolder.require();
        String resolved = providerId != null && !providerId.isBlank() ? providerId : userId;
        if (resolved == null || resolved.isBlank()) {
            resolved = ctx.actorId();
        }
        if (resolved == null || resolved.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "MISSING_PROVIDER", "A provider is required to resolve the current shift.",
                    400, ctx.correlationId().toString()));
        }

        return workspaceService.getCurrentShift(ctx.tenantId(), resolved)
                .map(shift -> ResponseEntity.ok(ApiResponse.ok(shift, ctx.correlationId().toString())))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error(
                        "NO_ACTIVE_SHIFT", "This provider is not currently on shift.",
                        404, ctx.correlationId().toString())));
    }

    /** Ends a shift. Idempotent — a handover confirmed twice must not rewrite when duty ended. */
    @PostMapping("/{shiftId}/end")
    public ResponseEntity<ApiResponse<ShiftResponse>> endShift(@PathVariable UUID shiftId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Ending shift {} correlationId={}", shiftId, ctx.correlationId());

        return workspaceService.endShift(ctx.tenantId(), shiftId)
                .map(shift -> ResponseEntity.ok(ApiResponse.ok(shift, ctx.correlationId().toString())))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error(
                        "SHIFT_NOT_FOUND", "No such shift for this tenant.",
                        404, ctx.correlationId().toString())));
    }
}
