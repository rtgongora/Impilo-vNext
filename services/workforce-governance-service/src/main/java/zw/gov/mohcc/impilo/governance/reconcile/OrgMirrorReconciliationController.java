package zw.gov.mohcc.impilo.governance.reconcile;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Read-only reconciliation report endpoint for the org-registry cutover soak.
 *
 * <p>{@code GET /internal/v1/workforce-governance/org-mirror/reconcile} pulls
 * the org-registry mirror inventory and diffs it against local ACTIVE
 * {@code wgv_organisation} rows. Additive + dormant: it mutates nothing and is
 * the operator's criterion-1 evidence (100% completeness + zero drift over the
 * soak window ⇒ eligible to proceed to phase-2c).
 */
@RestController
@RequestMapping("/internal/v1/workforce-governance/org-mirror")
public class OrgMirrorReconciliationController {

    private final OrgMirrorReconciliationService reconciliationService;

    public OrgMirrorReconciliationController(OrgMirrorReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/reconcile")
    public ResponseEntity<ApiResponse<OrgMirrorReconciliationReport>> reconcile() {
        TrustContext ctx = TrustContextHolder.get();
        UUID tenantId = ctx != null ? ctx.tenantId() : null;
        UUID correlationId = ctx != null ? ctx.correlationId() : null;
        OrgMirrorReconciliationReport report = reconciliationService.reconcile(tenantId, correlationId);
        String corr = correlationId != null ? correlationId.toString() : "";
        return ResponseEntity.ok(ApiResponse.ok(report, corr));
    }
}
