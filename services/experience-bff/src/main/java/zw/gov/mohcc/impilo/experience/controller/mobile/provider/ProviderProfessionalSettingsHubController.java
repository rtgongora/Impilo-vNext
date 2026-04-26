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
 * Tier-3 wave 6: web professional settings zone landings for mobile.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/professional-settings")
public class ProviderProfessionalSettingsHubController {

    private final ProviderMobileHubService hubService;

    public ProviderProfessionalSettingsHubController(ProviderMobileHubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/hub")
    public ResponseEntity<Map<String, Object>> hub(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        List<Map<String, Object>> stub = List.of(
                section("settings", "Settings", "/settings", "Professional preferences overview."),
                section("settings_account", "Account Settings", "/settings/account", "Profile, language, and sign-in identity."),
                section("settings_security", "Security Settings", "/settings/security", "MFA, sessions, and device posture."),
                section("settings_notifications", "Notification Preferences", "/settings/notifications", "Channels and alert rules."),
                section("settings_display", "Display Settings", "/settings/display", "Density, contrast, and accessibility."),
                section("settings_integrations", "Integrations", "/settings/integrations", "Connected apps and API tokens."),
                section("settings_privacy", "Privacy & Data", "/settings/privacy", "Retention, export, and consent mirrors.")
        );
        List<Map<String, Object>> sections = hubService.sectionsForHub("professional-settings", stub);
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
