package zw.gov.mohcc.impilo.obs.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.obs.core.DashboardService;
import zw.gov.mohcc.impilo.obs.core.DashboardType;
import zw.gov.mohcc.impilo.obs.persistence.entity.DashboardEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DashboardEntity>> createDashboard(
            @RequestBody CreateDashboardRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        DashboardEntity dashboard = dashboardService.createDashboard(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.name(), request.description(),
                request.dashboardType(), request.config());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(dashboard, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DashboardEntity>>> listDashboards() {
        TrustContext ctx = TrustContextHolder.require();

        List<DashboardEntity> dashboards = dashboardService.listDashboards(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(dashboards, ctx.correlationId().toString()));
    }

    public record CreateDashboardRequest(
            String name,
            String description,
            DashboardType dashboardType,
            String config
    ) {}
}
