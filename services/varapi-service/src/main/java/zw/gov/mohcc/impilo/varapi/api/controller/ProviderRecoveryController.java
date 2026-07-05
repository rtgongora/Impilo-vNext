package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryCompleteRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryCompleteResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryInitiateRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryInitiateResponse;
import zw.gov.mohcc.impilo.varapi.core.ProviderRecoveryService;

import java.util.Map;

/**
 * Provider profile recovery (IATG WS-F): <b>recover-not-reissue</b>.
 *
 * <p>Routes are gated by the standard Envoy ext_authz → TSHEPO path; the
 * service adds the defence-in-depth actor checks (only the person themselves,
 * or SYSTEM / REGISTRY_ADMIN assisted-desk capacities, may recover a profile).</p>
 *
 * <ul>
 *   <li>POST /v1/internal/providers/recovery/initiate — evidence in hand,
 *       issue a single-use recovery-purpose token bound to the person's
 *       EXISTING provider profile (404 if none — recovery never mints).</li>
 *   <li>POST /v1/internal/providers/recovery/complete — atomically redeem the
 *       token and re-link the login; the response carries the SAME
 *       {@code providerPublicId} the profile had before recovery.</li>
 * </ul>
 *
 * <p>Tokens travel in request bodies (never query strings) to keep them out of
 * access logs, matching {@link ProviderBootstrapController}.</p>
 */
@RestController
@RequestMapping("/v1/internal/providers/recovery")
public class ProviderRecoveryController {

    private final ProviderRecoveryService recoveryService;

    public ProviderRecoveryController(ProviderRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /** Issue a recovery-purpose token bound to the existing provider profile. */
    @PostMapping("/initiate")
    public ResponseEntity<RecoveryInitiateResponse> initiate(@RequestBody RecoveryInitiateRequest request) {
        return ResponseEntity.ok(recoveryService.initiate(request.healthId(), request.evidence()));
    }

    /** Redeem a recovery token and re-link the login to the SAME provider profile. */
    @PostMapping("/complete")
    public ResponseEntity<RecoveryCompleteResponse> complete(@RequestBody RecoveryCompleteRequest request) {
        return ResponseEntity.ok(recoveryService.complete(request.token()));
    }

    // ── Failure mapping (service throws plain exceptions; map to HTTP) ─────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException ex) {
        // Token invalid/expired/used, profile not recoverable, or the person
        // already owns a different provider profile.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
