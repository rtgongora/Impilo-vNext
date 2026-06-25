package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Telemedicine provider configuration for provider-neutral session routing.
 */
@Component
@ConfigurationProperties(prefix = "pct.telemedicine.provider")
public class TelemedicineProviderProperties {

    /** Provider used when a request does not name one — set to RTC_GATEWAY to default consults onto self-hosted LiveKit. */
    private String defaultProviderType = "MANAGED_PRIMARY";

    private External external = new External();
    private Fallback fallback = new Fallback();
    private Governance governance = new Governance();

    public String getDefaultProviderType() {
        return defaultProviderType;
    }

    public void setDefaultProviderType(String defaultProviderType) {
        this.defaultProviderType = defaultProviderType;
    }

    public External getExternal() {
        return external;
    }

    public void setExternal(External external) {
        this.external = external;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }

    public Governance getGovernance() {
        return governance;
    }

    public void setGovernance(Governance governance) {
        this.governance = governance;
    }

    public static class External {
        private boolean enabled = false;
        private String baseUrl = "";
        private String sessionPath = "/v1/sessions";
        private String apiKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSessionPath() {
            return sessionPath;
        }

        public void setSessionPath(String sessionPath) {
            this.sessionPath = sessionPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Fallback {
        private boolean allowManagedPrimaryFallback = true;

        public boolean isAllowManagedPrimaryFallback() {
            return allowManagedPrimaryFallback;
        }

        public void setAllowManagedPrimaryFallback(boolean allowManagedPrimaryFallback) {
            this.allowManagedPrimaryFallback = allowManagedPrimaryFallback;
        }
    }

    public static class Governance {
        private boolean requireConsentReferenceForMedia = true;
        private boolean allowEmergencyWithoutConsent = true;

        public boolean isRequireConsentReferenceForMedia() {
            return requireConsentReferenceForMedia;
        }

        public void setRequireConsentReferenceForMedia(boolean requireConsentReferenceForMedia) {
            this.requireConsentReferenceForMedia = requireConsentReferenceForMedia;
        }

        public boolean isAllowEmergencyWithoutConsent() {
            return allowEmergencyWithoutConsent;
        }

        public void setAllowEmergencyWithoutConsent(boolean allowEmergencyWithoutConsent) {
            this.allowEmergencyWithoutConsent = allowEmergencyWithoutConsent;
        }
    }
}
