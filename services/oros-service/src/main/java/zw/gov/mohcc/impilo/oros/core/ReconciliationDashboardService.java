package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.domain.DiagnosticReconcileBucket;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side operational reconciliation and turnaround dashboards for the diagnostics journey
 * (spec §H). Surfaces "stuck order" cohorts (by imaging-state dwell), unacknowledged critical
 * results, and a workload/turnaround distribution. Tenant-scoped from the trust context, with an
 * optional facility scope and SLA age threshold.
 */
@Service
public class ReconciliationDashboardService {

    private final OrderRepository orderRepository;
    private final ResultRepository resultRepository;

    public ReconciliationDashboardService(OrderRepository orderRepository,
                                          ResultRepository resultRepository) {
        this.orderRepository = orderRepository;
        this.resultRepository = resultRepository;
    }

    /** Orders stuck in the given reconciliation bucket (optionally only those older than N minutes). */
    public List<OrderEntity> stuck(DiagnosticReconcileBucket bucket, UUID facilityId, Integer olderThanMinutes) {
        TrustContext ctx = TrustContextHolder.require();
        OffsetDateTime before = olderThanMinutes != null
                ? OffsetDateTime.now().minusMinutes(olderThanMinutes) : null;
        return orderRepository.stuckImaging(ctx.tenantId(), facilityId, bucket.states(), before);
    }

    /** Unacknowledged critical results across the tenant. */
    public List<ResultEntity> criticalUnacknowledged() {
        TrustContext ctx = TrustContextHolder.require();
        return resultRepository.criticalUnacknowledged(ctx.tenantId());
    }

    /** Counts per reconciliation bucket plus the unacknowledged-critical count. */
    public Map<String, Long> summary(UUID facilityId, Integer olderThanMinutes) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (DiagnosticReconcileBucket bucket : DiagnosticReconcileBucket.values()) {
            out.put(bucket.name(), (long) stuck(bucket, facilityId, olderThanMinutes).size());
        }
        out.put("CRITICAL_UNACKNOWLEDGED", (long) criticalUnacknowledged().size());
        return out;
    }

    /** Workload distribution by imaging state for turnaround dashboards. */
    public Map<ImagingWorkflowState, Long> workloadByState(UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        Map<ImagingWorkflowState, Long> out = new EnumMap<>(ImagingWorkflowState.class);
        for (Object[] row : orderRepository.imagingStateCounts(ctx.tenantId(), facilityId)) {
            out.put((ImagingWorkflowState) row[0], (Long) row[1]);
        }
        return out;
    }
}
