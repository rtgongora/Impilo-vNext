package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.DashboardService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito/dashboards")
public class RitoDashboardController {

    private final DashboardService dashboardService;

    public RitoDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/facility")
    public Map<String, Object> facility(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID headerFacilityId,
            @RequestParam(value = "facility_id", required = false) UUID facilityId) {
        UUID resolved = facilityId != null ? facilityId : headerFacilityId;
        if (resolved == null) {
            throw new IllegalArgumentException("facility_id required");
        }
        return dashboardService.facilityDashboard(tenantId, resolved);
    }

    @GetMapping("/above-site")
    public Map<String, Object> aboveSite(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return dashboardService.aboveSiteDashboard(tenantId);
    }

    @GetMapping("/client-voice")
    public Map<String, Object> clientVoice(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return dashboardService.clientVoiceDashboard(tenantId);
    }

    @GetMapping("/safety")
    public Map<String, Object> safety(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return dashboardService.safetyDashboard(tenantId);
    }
}
