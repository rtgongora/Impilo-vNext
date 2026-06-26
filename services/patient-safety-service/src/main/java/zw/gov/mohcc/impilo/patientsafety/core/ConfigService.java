package zw.gov.mohcc.impilo.patientsafety.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference configuration surface: external VigiMobile / VigiFlow eForm link-outs and an HONEST
 * adapter posture. The links are EXTERNAL WHO-UMC / MCAZ destinations — Impilo links the reporter to
 * them but does not receive those submissions back unless a callback adapter is configured. The
 * adapter list reflects what integration-hub has connected; nothing here performs live submission.
 */
@Service
public class ConfigService {

    private final String vigiMobileAdrUrl;
    private final String vigiMobileAefiUrl;
    private final String mcazWhatsAppNumber;
    private final String mcazPortalUrl;

    private final boolean vigiflowE2bEnabled;
    private final boolean vigiMobileLinkEnabled;
    private final boolean whatsappEnabled;
    private final boolean smsEnabled;
    private final boolean ussdEnabled;
    private final boolean emailEnabled;
    private final boolean voiceEnabled;

    public ConfigService(
            @Value("${patientsafety.vigimobile.adr-url:}") String vigiMobileAdrUrl,
            @Value("${patientsafety.vigimobile.aefi-url:}") String vigiMobileAefiUrl,
            @Value("${patientsafety.vigimobile.mcaz-whatsapp-number:}") String mcazWhatsAppNumber,
            @Value("${patientsafety.vigimobile.mcaz-portal-url:}") String mcazPortalUrl,
            @Value("${patientsafety.adapters.vigiflow-e2b.enabled:false}") boolean vigiflowE2bEnabled,
            @Value("${patientsafety.adapters.vigimobile-link.enabled:true}") boolean vigiMobileLinkEnabled,
            @Value("${patientsafety.adapters.whatsapp.enabled:false}") boolean whatsappEnabled,
            @Value("${patientsafety.adapters.sms.enabled:false}") boolean smsEnabled,
            @Value("${patientsafety.adapters.ussd.enabled:false}") boolean ussdEnabled,
            @Value("${patientsafety.adapters.email.enabled:false}") boolean emailEnabled,
            @Value("${patientsafety.adapters.voice.enabled:false}") boolean voiceEnabled) {
        this.vigiMobileAdrUrl = vigiMobileAdrUrl;
        this.vigiMobileAefiUrl = vigiMobileAefiUrl;
        this.mcazWhatsAppNumber = mcazWhatsAppNumber;
        this.mcazPortalUrl = mcazPortalUrl;
        this.vigiflowE2bEnabled = vigiflowE2bEnabled;
        this.vigiMobileLinkEnabled = vigiMobileLinkEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.smsEnabled = smsEnabled;
        this.ussdEnabled = ussdEnabled;
        this.emailEnabled = emailEnabled;
        this.voiceEnabled = voiceEnabled;
    }

    public boolean isVigiflowAdapterEnabled() {
        return vigiflowE2bEnabled;
    }

    /** External WHO-UMC VigiMobile / VigiFlow eForm and MCAZ channel links (labelled EXTERNAL). */
    public Map<String, Object> vigiMobileLinks() {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("disclaimer", "External WHO-UMC / MCAZ destinations. Submitting via these links sends "
                + "the report directly to the external system; Impilo does not receive a copy unless a "
                + "callback adapter is configured.");
        links.put("adr_eform_url", vigiMobileAdrUrl);
        links.put("aefi_eform_url", vigiMobileAefiUrl);
        links.put("mcaz_whatsapp_number", mcazWhatsAppNumber);
        links.put("mcaz_portal_url", mcazPortalUrl);
        links.put("kind", "EXTERNAL_LINK_OUT");
        return links;
    }

    /** Honest adapter posture for the workbench export-readiness / dispatch indicators. */
    public List<Map<String, Object>> adapters() {
        return List.of(
                adapter("vigiflow-e2b", "VigiFlow E2B(R3) submission", "DISPATCH", vigiflowE2bEnabled,
                        vigiflowE2bEnabled
                                ? "Connected via integration-hub."
                                : "Not configured. Use manual VigiFlow entry; export package is E2B(R3)-aligned, not a certified transmission."),
                adapter("vigimobile-link", "WHO-UMC VigiMobile / VigiFlow eForm link-out", "EXTERNAL_LINK",
                        vigiMobileLinkEnabled,
                        "External reporter link-out. Impilo does not receive submissions made on the external eForm."),
                adapter("whatsapp", "MCAZ WhatsApp PV channel", "DISPATCH", whatsappEnabled,
                        whatsappEnabled ? "Connected." : "Reference channel only — not connected for inbound capture."),
                adapter("sms", "SMS solicitation / reply", "DISPATCH", smsEnabled,
                        smsEnabled ? "Connected." : "Not configured."),
                adapter("ussd", "USSD reporting", "DISPATCH", ussdEnabled,
                        ussdEnabled ? "Connected." : "Not configured."),
                adapter("email", "Email solicitation / reply", "DISPATCH", emailEnabled,
                        emailEnabled ? "Connected." : "Not configured."),
                adapter("voice", "Voice / call-centre intake", "DISPATCH", voiceEnabled,
                        voiceEnabled ? "Connected." : "Not configured."));
    }

    private static Map<String, Object> adapter(String key, String label, String kind,
                                               boolean enabled, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("kind", kind);
        m.put("enabled", enabled);
        m.put("status", status);
        return m;
    }
}
