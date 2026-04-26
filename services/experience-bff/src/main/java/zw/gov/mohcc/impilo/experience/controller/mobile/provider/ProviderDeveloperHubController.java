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
 * Tier-3 wave 5: developer portal professional-plane landings for mobile.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/developer")
public class ProviderDeveloperHubController {

    private final ProviderMobileHubService hubService;

    public ProviderDeveloperHubController(ProviderMobileHubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/hub")
    public ResponseEntity<Map<String, Object>> hub(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        List<Map<String, Object>> stub = List.of(
                section("developer", "Developer Portal", "/developer", "Integrator landing and documentation."),
                section("developer_api_catalog", "API Catalog", "/developer/api-catalog", "Browse versioned APIs and schemas."),
                section("developer_clients", "Client Registration", "/developer/clients", "Register OAuth clients and callbacks."),
                section("developer_sandbox", "Sandbox", "/developer/sandbox", "Test tenants and sample data access.")
        );
        List<Map<String, Object>> sections = hubService.sectionsForHub("developer", stub);
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
