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
 * Tier-3 wave 7: omnichannel, coverage, credentials, and public-health drill-down landings.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/professional-channels")
public class ProviderProfessionalChannelsHubController {

    private final ProviderMobileHubService hubService;

    public ProviderProfessionalChannelsHubController(ProviderMobileHubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/hub")
    public ResponseEntity<Map<String, Object>> hub(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        List<Map<String, Object>> stub = List.of(
                section("omnichannel", "Omnichannel Hub", "/omnichannel", "Queues, messaging, and channel routing."),
                section("coverage", "Coverage Operations", "/coverage", "Schemes, eligibility, and verification."),
                section("home_credentials", "Credentials & CPD", "/home/credentials", "Professional licenses and learning credits."),
                section("ph_surveillance", "Surveillance", "/public-health/surveillance", "Signals, case lines, and indicators."),
                section("ph_campaigns", "Campaigns", "/public-health/campaigns", "Immunisation and outreach waves."),
                section("ph_site_registry", "Site Registry", "/public-health/site-registry", "Community sites and outreach anchors."),
                section("ph_site_profile", "Site Profile", "/public-health/site-registry/[siteId]", "Single-site programme detail.")
        );
        List<Map<String, Object>> sections = hubService.sectionsForHub("professional-channels", stub);
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
