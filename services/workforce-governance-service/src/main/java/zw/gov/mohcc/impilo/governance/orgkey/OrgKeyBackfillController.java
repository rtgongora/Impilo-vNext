package zw.gov.mohcc.impilo.governance.orgkey;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Callable-only (admin) endpoint that runs the phase-2c dual-key backfill.
 *
 * <p>{@code POST /internal/v1/workforce-governance/org-mirror/backfill-keys}
 * populates the additive, nullable {@code org_registry_org_id} columns from the
 * mirror inventory. It is DORMANT: it is never invoked automatically and no-ops
 * (reporting {@code enabled=false}) while {@code impilo.org-mirror.enabled} is
 * off. It only ever writes the forward key — the authoritative
 * {@code organisation_id} FK is untouched, so this is reversible until the
 * irreversible phase-2c flip.
 */
@RestController
@RequestMapping("/internal/v1/workforce-governance/org-mirror")
public class OrgKeyBackfillController {

    private final OrgKeyBackfillService backfillService;

    public OrgKeyBackfillController(OrgKeyBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/backfill-keys")
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfillKeys() {
        TrustContext ctx = TrustContextHolder.get();
        UUID tenantId = ctx != null ? ctx.tenantId() : null;
        UUID correlationId = ctx != null ? ctx.correlationId() : null;
        OrgKeyBackfillService.OrgKeyBackfillResult result = backfillService.backfill(tenantId, correlationId);
        Map<String, Object> body = Map.of(
                "enabled", result.enabled(),
                "mappedRefs", result.mappedRefs(),
                "updatedByTable", result.updatedByTable());
        String corr = correlationId != null ? correlationId.toString() : "";
        return ResponseEntity.ok(ApiResponse.ok(body, corr));
    }
}
