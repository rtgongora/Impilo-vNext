package zw.gov.mohcc.impilo.governance.mirror;

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
 * Admin endpoint to (re-)run the one-way organisation mirror backfill.
 *
 * <p>{@code POST /internal/v1/workforce-governance/org-mirror/backfill} sweeps
 * all active {@code wgv_organisation} rows and pushes each to
 * organization-registry. Idempotent on the receiver, so it is safe to re-run.
 * No-op (and reports {@code enabled=false}) while the producer flag is off.
 */
@RestController
@RequestMapping("/internal/v1/workforce-governance/org-mirror")
public class OrgMirrorBackfillController {

    private final OrgMirrorBackfillService backfillService;

    public OrgMirrorBackfillController(OrgMirrorBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfill() {
        TrustContext ctx = TrustContextHolder.get();
        UUID correlationId = ctx != null ? ctx.correlationId() : null;
        OrgMirrorBackfillService.BackfillResult result = backfillService.backfill(correlationId);
        Map<String, Object> body = Map.of(
                "enabled", result.enabled(),
                "total", result.total(),
                "pushed", result.pushed(),
                "failed", result.failed(),
                "skipped", result.skipped());
        String corr = correlationId != null ? correlationId.toString() : "";
        return ResponseEntity.ok(ApiResponse.ok(body, corr));
    }
}
