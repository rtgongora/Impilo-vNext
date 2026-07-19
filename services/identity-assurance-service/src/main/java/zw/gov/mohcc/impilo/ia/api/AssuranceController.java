package zw.gov.mohcc.impilo.ia.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ia.core.AssuranceLevel;
import zw.gov.mohcc.impilo.ia.core.AssuranceService;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceUpgradeRequestEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Identity assurance API (Wave C C2a) — the real source-of-record the experience-bff proxies
 * (replacing the previous BFF fabrication). Surfaces an actor's assurance status and the
 * upgrade-request review workflow.
 */
@RestController
@RequestMapping("/internal/v1/assurance")
public class AssuranceController {

    private final AssuranceService assuranceService;

    public AssuranceController(AssuranceService assuranceService) {
        this.assuranceService = assuranceService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AssuranceService.StatusView>> status() {
        TrustContext ctx = TrustContextHolder.require();
        AssuranceService.StatusView view = assuranceService.getStatus(ctx.tenantId(), ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(view, ctx.correlationId().toString()));
    }

    @PostMapping("/upgrade/request")
    public ResponseEntity<ApiResponse<AssuranceUpgradeRequestEntity>> requestUpgrade(
            @RequestBody UpgradeRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        AssuranceUpgradeRequestEntity request = assuranceService.requestUpgrade(
                ctx.tenantId(), ctx.actorId(), body.targetLevel(), body.method());
        return ResponseEntity.status(201).body(ApiResponse.ok(request, ctx.correlationId().toString()));
    }

    @GetMapping("/upgrade/requests")
    public ResponseEntity<ApiResponse<List<AssuranceUpgradeRequestEntity>>> listPending() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                assuranceService.listPending(ctx.tenantId()), ctx.correlationId().toString()));
    }

    @PostMapping("/upgrade/requests/{id}/decide")
    public ResponseEntity<ApiResponse<AssuranceUpgradeRequestEntity>> decide(
            @PathVariable Long id, @RequestBody DecideRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        AssuranceUpgradeRequestEntity decided = assuranceService.decideUpgrade(
                ctx.tenantId(), ctx.actorId(), id, body.approve(), body.reason());
        return ResponseEntity.ok(ApiResponse.ok(decided, ctx.correlationId().toString()));
    }

    /**
     * Raise the CURRENT actor's assurance from an authoritative proofing outcome (Identity
     * Journey Doctrine §4). INTERNAL-only — only the Trust Core, after a genuine proofing
     * (auto document-verify or officer-witnessed review), may call this; a citizen can never
     * self-grant. The level is applied to {@code ctx.actorId()} (the acting citizen), so no
     * actor can raise another's assurance. Only ever raises.
     */
    @PostMapping("/proofing-outcome")
    public ResponseEntity<ApiResponse<AssuranceService.StatusView>> proofingOutcome(
            @RequestBody ProofingOutcomeRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != zw.gov.mohcc.impilo.shared.auth.AccessMode.INTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "proofing-outcome is INTERNAL-only");
        }
        // Self-service proofing raises the CURRENT actor. The officer-witnessed path (a steward
        // approving another person's review) passes an explicit actorId — allowed only because
        // this endpoint is INTERNAL-only and every raise is audited with its provenance.
        String targetActor = (body.actorId() != null && !body.actorId().isBlank())
                ? body.actorId().trim() : ctx.actorId();
        AssuranceService.StatusView view = assuranceService.recordProofingOutcome(
                ctx.tenantId(), targetActor, body.targetLevel(), body.provenance());
        return ResponseEntity.ok(ApiResponse.ok(view, ctx.correlationId().toString()));
    }

    /**
     * Bind the actor to an LOA4 biometric factor from a live probe (Access-Control dimension
     * "assurance level"). INTERNAL-only — the Trust Core presents a subjectRef + captured probe;
     * the probe is verified through the shared ABIS seam. MATCH raises the actor to LOA4;
     * NO_MATCH is a 403 denial with no elevation; an ABIS outage leaves the level unchanged.
     * The raise is applied to the CURRENT actor unless an explicit actorId is supplied
     * (officer-witnessed capture), and every bind is audited with its provenance.
     */
    @PostMapping("/biometric-binding")
    public ResponseEntity<ApiResponse<AssuranceService.StatusView>> biometricBinding(
            @RequestBody BiometricBindingRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != zw.gov.mohcc.impilo.shared.auth.AccessMode.INTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "biometric-binding is INTERNAL-only");
        }
        String targetActor = (body.actorId() != null && !body.actorId().isBlank())
                ? body.actorId().trim() : ctx.actorId();
        try {
            AssuranceService.StatusView view = assuranceService.recordBiometricBinding(
                    ctx.tenantId(), targetActor, body.subjectRef(), body.modality(), body.probeBase64());
            return ResponseEntity.ok(ApiResponse.ok(view, ctx.correlationId().toString()));
        } catch (SecurityException ex) {
            // NO_MATCH → explicit denial; no elevation.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    public record UpgradeRequest(AssuranceLevel targetLevel, String method) {}

    public record DecideRequest(boolean approve, String reason) {}

    public record ProofingOutcomeRequest(AssuranceLevel targetLevel, String provenance, String actorId) {}

    public record BiometricBindingRequest(String subjectRef, String modality, String probeBase64, String actorId) {}
}
