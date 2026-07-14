package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.QueueMaterializationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

/**
 * On-demand reconciliation of a facility's queue definitions FROM TUSO, plus a materialisation-status
 * read. This is the reconciliation path that complements event-driven materialisation — events can be
 * missed/delayed/replayed, so operators (and schedulers) can pull the current TUSO configuration on
 * demand.
 *
 * <p>This is NOT a queue create/edit surface — PCT never authors queue definitions; it materialises
 * them from TUSO (the facility service-point/workspace system of record).</p>
 */
@RestController
@RequestMapping("/v1/internal/queues")
public class QueueReconciliationController {

    private final QueueMaterializationService materializationService;
    private final zw.gov.mohcc.impilo.pct.core.VirtualPoolQueueService virtualPoolQueueService;

    public QueueReconciliationController(QueueMaterializationService materializationService,
                                         zw.gov.mohcc.impilo.pct.core.VirtualPoolQueueService virtualPoolQueueService) {
        this.materializationService = materializationService;
        this.virtualPoolQueueService = virtualPoolQueueService;
    }

    /** Reconcile the facility's queues from TUSO's current queue definitions (idempotent, failure-safe). */
    @PostMapping("/reconcile")
    public ResponseEntity<ApiResponse<QueueMaterializationService.MaterializationResult>> reconcile(
            @RequestParam UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        var result = materializationService.reconcileFacility(ctx.tenantId(), facilityId);
        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    /** Materialisation-status summary for a facility (materialised vs seed/demo, counts, last sync). */
    @GetMapping("/materialization-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> materializationStatus(
            @RequestParam UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        var summary = materializationService.materializationStatus(ctx.tenantId(), facilityId);
        return ResponseEntity.ok(ApiResponse.ok(summary, ctx.correlationId().toString()));
    }

    /**
     * On-demand reconcile of the tenant's VIRTUAL-POOL queues from TUSO's
     * activatable virtual services (idempotent, failure-safe). Complements the
     * event-driven trigger ({@code impilo.tuso.virtual_service}) — events can be
     * missed/delayed/replayed.
     */
    @PostMapping("/reconcile-virtual-pools")
    public ResponseEntity<ApiResponse<java.util.List<QueueMaterializationService.PoolMaterializationResult>>>
            reconcileVirtualPools() {
        TrustContext ctx = TrustContextHolder.require();
        var results = materializationService.reconcileVirtualPools(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(results, ctx.correlationId().toString()));
    }

    /**
     * Per-queue statistics for one virtual pool (depth, oldest wait, SLA
     * breaches, materialisation state) — the BFF composes LIVE vs
     * AWAITING_BACKEND from this read-back.
     */
    @GetMapping("/virtual-pool-stats")
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> virtualPoolStats(
            @RequestParam String poolId) {
        TrustContext ctx = TrustContextHolder.require();
        var stats = virtualPoolQueueService.poolQueueStats(ctx.tenantId(), poolId);
        return ResponseEntity.ok(ApiResponse.ok(stats, ctx.correlationId().toString()));
    }
}
