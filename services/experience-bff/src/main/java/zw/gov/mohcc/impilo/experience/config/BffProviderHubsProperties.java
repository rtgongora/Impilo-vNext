package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider professional-plane hub metadata mode switch.
 *
 * <p>When {@code live}, hub sections are fetched from a downstream gateway (currently one-ui-shell)
 * so the BFF does not own hard-coded navigation scaffolding.</p>
 */
@ConfigurationProperties(prefix = "impilo.bff.provider-hubs")
public class BffProviderHubsProperties {

    private Mode mode = Mode.stub;
    private FailurePolicy failurePolicy = FailurePolicy.stub_fallback;
    private String oneUiShellBaseUrl = "http://localhost:3000";

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.stub;
    }

    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy != null ? failurePolicy : FailurePolicy.stub_fallback;
    }

    public String getOneUiShellBaseUrl() {
        return oneUiShellBaseUrl;
    }

    public void setOneUiShellBaseUrl(String oneUiShellBaseUrl) {
        if (oneUiShellBaseUrl != null && !oneUiShellBaseUrl.isBlank()) {
            this.oneUiShellBaseUrl = oneUiShellBaseUrl.trim();
        }
    }

    public enum Mode {
        stub,
        live
    }

    public enum FailurePolicy {
        stub_fallback,
        propagate
    }
}

