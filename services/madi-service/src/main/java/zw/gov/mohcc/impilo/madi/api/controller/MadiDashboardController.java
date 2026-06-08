package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.DashboardService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/dashboard")
public class MadiDashboardController {

    private final DashboardService dashboardService;

    public MadiDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Map<String, Object> dashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(value = "scope", defaultValue = "local") String scope,
            @RequestParam(value = "facility_id", required = false) UUID facilityId,
            @RequestParam(value = "drive_id", required = false) UUID driveId) {
        return switch (scope) {
            case "facility" -> {
                if (facilityId == null) {
                    throw new IllegalArgumentException("facility_id required for facility scope");
                }
                yield dashboardService.facilityMetrics(tenantId, facilityId);
            }
            case "central" -> dashboardService.centralMetrics(tenantId);
            case "drive" -> {
                if (driveId == null) {
                    throw new IllegalArgumentException("drive_id required for drive scope");
                }
                yield dashboardService.driveMetrics(tenantId, driveId);
            }
            default -> dashboardService.localMetrics(tenantId);
        };
    }

    @GetMapping("/forecast")
    public Map<String, Object> forecast(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(value = "facility_id", required = false) UUID facilityId) {
        return dashboardService.thirtyDayForecast(tenantId, facilityId);
    }
}
