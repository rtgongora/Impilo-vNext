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
 * Tier-3 wave 3: mobile entry for admin + registry professional-plane landings.
 *
 * Thin BFF-owned surface so the provider app can sync section metadata while
 * sovereign admin/registry services are integrated in later waves.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/admin-registry")
public class ProviderAdminRegistryController {

    private final ProviderMobileHubService hubService;

    public ProviderAdminRegistryController(ProviderMobileHubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/hub")
    public ResponseEntity<Map<String, Object>> hub(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        List<Map<String, Object>> stub = List.of(
                section("admin", "Administration", "/admin", "Users, roles, policies, and platform configuration."),
                section("registry", "Registry Hub", "/registry", "Patient and provider registry entry points."),
                section("registry_admin", "Registry Administration", "/registry-admin", "Registry configuration and governance."),
                section("organization_admin", "Organization Administration", "/organization-admin", "Sites, cadres, and org structure."),
                section("public_health", "Public Health", "/public-health", "Programmes, surveillance, and campaigns."),
                section("id_services", "Identity Services", "/id-services", "Identity proofing and credential services."),
                section("access", "Access Channels", "/access", "Kiosk, landline, and alternate access paths."),
                section("ai_governance", "AI Governance", "/ai-governance", "Model registry, safety, and audit controls.")
        );
        List<Map<String, Object>> sections = hubService.sectionsForHub("admin-registry", stub);
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
