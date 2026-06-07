package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.DashboardService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/central-bank")
public class MadiCentralBankController {

    private final DashboardService dashboardService;

    public MadiCentralBankController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> centralMetrics(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return dashboardService.centralMetrics(tenantId);
    }
}
