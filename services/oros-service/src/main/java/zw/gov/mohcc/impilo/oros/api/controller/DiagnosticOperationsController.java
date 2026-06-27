package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.ReportDto;
import zw.gov.mohcc.impilo.oros.core.ReconciliationDashboardService;
import zw.gov.mohcc.impilo.oros.domain.DiagnosticReconcileBucket;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Operational reconciliation and turnaround dashboards for the diagnostics journey (spec §H).
 *
 * <p>Distinct from the hybrid-routing reconcile queue at {@code /v1/reconcile/{pending,match,
 * resolve}}: these endpoints surface stuck-order cohorts, unacknowledged critical results, and
 * workload/turnaround metrics over the imaging lifecycle.</p>
 */
@RestController
public class DiagnosticOperationsController {

    private final ReconciliationDashboardService dashboardService;

    public DiagnosticOperationsController(ReconciliationDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Counts per reconciliation bucket plus unacknowledged-critical count. */
    @GetMapping("/v1/reconcile/diagnostics/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> summary(
            @RequestParam(name = "facility", required = false) UUID facility,
            @RequestParam(name = "olderThanMinutes", required = false) Integer olderThanMinutes) {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary(facility, olderThanMinutes), cid));
    }

    /** Orders stuck in a given reconciliation bucket. */
    @GetMapping("/v1/reconcile/diagnostics/stuck")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> stuck(
            @RequestParam(name = "bucket") DiagnosticReconcileBucket bucket,
            @RequestParam(name = "facility", required = false) UUID facility,
            @RequestParam(name = "olderThanMinutes", required = false) Integer olderThanMinutes) {
        String cid = TrustContextHolder.require().correlationId().toString();
        List<OrderSummaryDto> orders = dashboardService.stuck(bucket, facility, olderThanMinutes).stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(orders, cid));
    }

    /** Unacknowledged critical results requiring escalation/follow-up. */
    @GetMapping("/v1/reconcile/diagnostics/critical-unacknowledged")
    public ResponseEntity<ApiResponse<List<ReportDto>>> criticalUnacknowledged() {
        String cid = TrustContextHolder.require().correlationId().toString();
        List<ReportDto> results = dashboardService.criticalUnacknowledged().stream()
                .map(ReportDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(results, cid));
    }

    /** Imaging workload/turnaround distribution by state. */
    @GetMapping("/v1/metrics/turnaround")
    public ResponseEntity<ApiResponse<TurnaroundMetrics>> turnaround(
            @RequestParam(name = "facility", required = false) UUID facility) {
        String cid = TrustContextHolder.require().correlationId().toString();
        Map<ImagingWorkflowState, Long> byState = dashboardService.workloadByState(facility);
        long total = byState.values().stream().mapToLong(Long::longValue).sum();
        return ResponseEntity.ok(ApiResponse.ok(new TurnaroundMetrics(total, byState), cid));
    }

    /** Workload/turnaround metrics payload. */
    public record TurnaroundMetrics(long totalImaging, Map<ImagingWorkflowState, Long> byState) {}
}
