package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.BulkPreloadRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.BulkPreloadResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ClaimProfileRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ClaimProfileResponse;
import zw.gov.mohcc.impilo.varapi.core.HpaPractitionerClaimService;
import zw.gov.mohcc.impilo.varapi.core.ProviderBootstrapService;

import java.util.Map;

/**
 * Provider bootstrap chain (L3 W6): national-admin → org → representatives →
 * <b>bulk preload</b> → provider <b>self-claim</b>.
 *
 * <p>Routes are gated by the standard Envoy ext_authz → TSHEPO path. The
 * caller-role rules are specified (not authored here) in the Tshepo policy
 * contract list:
 * <ul>
 *   <li>TODO(policy PROVIDER-PRELOAD-ADMIN): only national-admin / org-rep may bulk-preload.</li>
 *   <li>TODO(policy PROVIDER-SELF-CLAIM): a claim binds only to the authenticated
 *       claimant's own Health ID (enforced below as a defence-in-depth check).</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/internal/providers/bootstrap")
public class ProviderBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(ProviderBootstrapController.class);

    private final ProviderBootstrapService bootstrapService;
    private final HpaPractitionerClaimService hpaClaimService;

    public ProviderBootstrapController(ProviderBootstrapService bootstrapService,
                                       HpaPractitionerClaimService hpaClaimService) {
        this.bootstrapService = bootstrapService;
        this.hpaClaimService = hpaClaimService;
    }

    /** Bulk-preload a roster of PRELOADED provider skeletons; each yields a one-time claim token. */
    @PostMapping("/preload")
    public ResponseEntity<BulkPreloadResponse> bulkPreload(@Valid @RequestBody BulkPreloadRequest request) {
        return ResponseEntity.ok(bootstrapService.bulkPreload(request));
    }

    /**
     * Read-only preview of what a claim token would claim, so the claimant can
     * confirm before committing. Token is sent in the body (not the query
     * string) to keep it out of access logs. Returns 404 for any non-redeemable
     * token without revealing why (a token cannot be probed).
     */
    @PostMapping("/claim/preview")
    public ResponseEntity<ClaimProfileResponse> previewClaim(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("claimToken");
        return bootstrapService.previewClaim(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Self-claim a preloaded profile. The claimant has already authenticated as
     * a person (person-first login); we cross-check that the claimed Health ID
     * is the authenticated actor's own — a token can never be redeemed onto an
     * arbitrary anchor (provider-as-person separation, defence in depth).
     */
    @PostMapping("/claim")
    public ResponseEntity<ClaimProfileResponse> claim(@Valid @RequestBody ClaimProfileRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.actorId() == null || !ctx.actorId().equals(request.claimantHealthId().toString())) {
            // The authenticated person may only claim onto their own Health ID.
            log.warn("provider self-claim rejected: claimant {} != authenticated actor",
                    request.claimantHealthId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ClaimProfileResponse response = bootstrapService.claimProfile(
                request.claimToken(), request.claimantHealthId(), request.assuranceOutcome());
        return ResponseEntity.ok(response);
    }

    /**
     * HAR W3 — claim an HPA-listed profile by council registration number.
     *
     * <p>The 4,241 practitioners HPA listed as practitioners-in-charge hold no claim token: the
     * import had no contact channel for them, so {@link #claim} is unreachable for exactly the
     * people the register is about. This records a request for a human decision instead — it binds
     * nothing, and returns the same acknowledgement whether or not the number resolves, so the
     * endpoint cannot be used to discover which registration numbers exist.</p>
     *
     * <p>Both branches do identical work (submit + save), so the response time carries no signal
     * either; no artificial floor is needed here, unlike the read-only resolve lane.</p>
     */
    @PostMapping("/claim/by-registration-number")
    public ResponseEntity<HpaPractitionerClaimService.ClaimAcknowledgement> claimByRegistrationNumber(
            @RequestBody Map<String, String> body) {
        TrustContextHolder.require();
        String registrationNumber = body == null ? null : body.get("registrationNumber");
        String councilCode = body == null ? null : body.get("councilCode");
        return ResponseEntity.ok(
                hpaClaimService.claimByRegistrationNumber(registrationNumber, councilCode));
    }

    /**
     * Opt the authenticated person's own provider profile in or out of participation.
     *
     * <p>Registration is not participation: being on the register makes a practitioner findable
     * and verifiable, while this switch decides whether they can be booked. The profile is
     * resolved from the authenticated actor server-side and never from the body — a caller must
     * not be able to nominate whose participation they are changing.</p>
     */
    @PostMapping("/participation")
    public ResponseEntity<ClaimProfileResponse> setParticipation(@RequestBody Map<String, Object> body) {
        TrustContextHolder.require();
        Object value = body == null ? null : body.get("participating");
        if (!(value instanceof Boolean participating)) {
            throw new IllegalArgumentException("participating must be true or false");
        }
        return ResponseEntity.ok(bootstrapService.setParticipation(participating));
    }

    // ── Failure mapping (service throws plain exceptions; map to HTTP) ─────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException ex) {
        // Token invalid/expired/used, or profile not claimable, or person already a provider.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
