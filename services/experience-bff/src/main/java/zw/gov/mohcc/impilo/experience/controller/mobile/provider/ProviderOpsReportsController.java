package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.provider.ProviderMobileHubService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier-3 wave 4: operations + reports professional-plane landings for mobile.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/ops-reports")
public class ProviderOpsReportsController {

    private final ProviderMobileHubService hubService;

    public ProviderOpsReportsController(ProviderMobileHubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/hub")
    public ResponseEntity<Map<String, Object>> hub(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        List<Map<String, Object>> stub = List.of(
                section("operations", "Operations", "/operations", "Day-to-day facility and platform operations."),
                section("operations_vito", "Identity Operations", "/operations/vito", "VITO / identity exchange operations."),
                section("operations_butano", "SHR Operations", "/operations/butano", "Shared health record connectivity and operations."),
                section("operations_assets", "Asset Management", "/operations/assets", "Track and maintain physical assets."),
                section("operations_equipment", "Equipment Management", "/operations/equipment", "Devices, maintenance, and calibration."),
                section("reports", "Reports", "/reports", "Reporting home and saved views."),
                section("reports_facility", "Facility Reports", "/reports/facility", "Utilization, throughput, and site KPIs."),
                section("reports_clinical", "Clinical Reports", "/reports/clinical", "Clinical quality and outcomes summaries."),
                section("reports_operational", "Operational Reports", "/reports/operational", "Ops dashboards and SLAs."),
                section("reports_custom", "Custom Reports", "/reports/custom", "User-defined report definitions."),
                section("reports_detail", "Report Details", "/reports/[id]", "Drill into a single report run or export.")
        );
        List<Map<String, Object>> sections = hubService.sectionsForHub("ops-reports", stub);
        return ResponseEntity.ok(hubService.hubEnvelope(requestId, correlationId, sections));
    }

    private static Map<String, Object> section(String id, String title, String webPath, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("web_path", webPath);
        m.put("hint", hint);
        return m;
    }
}
