package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.RegisterMaterialisationService;

/**
 * Operator-triggered reconcile of a council's registers against its ACTIVE configuration (task #97).
 *
 * <p>The event consumer keeps registers current as releases activate; this endpoint exists for the
 * cases an event cannot cover — a council linked to its organisation after activation, a service
 * that was down when the event was published, or an operator who needs to prove the current state
 * matches the current configuration. It is deliberately idempotent, so running it because you are
 * unsure is always safe.</p>
 *
 * <p>Reconcile reads configuration and writes only {@code professional_registers}. It cannot create
 * a register from a DRAFT release: it resolves through the Registry's active-configuration endpoint,
 * which has nothing to return until a release is activated.</p>
 */
@RestController
@RequestMapping("/v1/internal/councils")
public class RegisterMaterialisationController {

    private final RegisterMaterialisationService materialisationService;

    public RegisterMaterialisationController(RegisterMaterialisationService materialisationService) {
        this.materialisationService = materialisationService;
    }

    @PostMapping("/{councilId}/registers/reconcile")
    public ResponseEntity<RegisterMaterialisationService.ReconcileReport> reconcile(
            @PathVariable Long councilId) {
        TrustContext ctx = TrustContextHolder.require();
        String actor = ctx.actorId() == null ? "system" : ctx.actorId();
        return ResponseEntity.ok(
                materialisationService.reconcile(ctx.tenantId(), councilId, actor));
    }
}
