package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.CentralBankService;
import zw.gov.mohcc.impilo.madi.core.DashboardService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/central-bank")
public class MadiCentralBankController {

    private final DashboardService dashboardService;
    private final CentralBankService centralBankService;

    public MadiCentralBankController(DashboardService dashboardService,
                                     CentralBankService centralBankService) {
        this.dashboardService = dashboardService;
        this.centralBankService = centralBankService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> centralMetrics(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return dashboardService.centralMetrics(tenantId);
    }

    @GetMapping("/emergency-redistributions")
    public List<Map<String, Object>> emergencyRedistributions(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return centralBankService.listEmergencyRedistributions(tenantId);
    }

    @PostMapping("/emergency-redistributions")
    public Map<String, Object> requestEmergencyRedistribution(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        return centralBankService.requestEmergencyRedistribution(tenantId, body);
    }

    @PostMapping("/emergency-redistributions/{redistributionId}/approve")
    public Map<String, Object> approveEmergencyRedistribution(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID redistributionId,
            @RequestBody Map<String, Object> body) {
        return centralBankService.approveEmergencyRedistribution(tenantId, redistributionId, body);
    }

    @PostMapping("/emergency-redistributions/{redistributionId}/handoff")
    public Map<String, Object> initiatePhysicalHandoff(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID redistributionId,
            @RequestBody(required = false) Map<String, Object> body) {
        String actorId = body != null && body.get("initiated_by") != null
                ? body.get("initiated_by").toString()
                : "central-ops";
        return centralBankService.initiatePhysicalHandoff(tenantId, redistributionId, actorId);
    }
}
