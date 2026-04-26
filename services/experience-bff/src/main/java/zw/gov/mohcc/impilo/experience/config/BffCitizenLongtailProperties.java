package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls whether citizen long-tail mobile endpoints use in-process stubs or
 * delegate to sovereign services (VITO patient-share, credential-verification public verify).
 *
 * <p>Environment:</p>
 * <ul>
 *   <li>{@code IMPILO_BFF_CITIZEN_LONGTAIL_MODE} — {@code stub} (default) or {@code live}</li>
 *   <li>{@code IMPILO_BFF_CITIZEN_LONGTAIL_FAILURE_POLICY} — {@code stub_fallback} (default) or {@code propagate}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "impilo.bff.citizen-longtail")
public class BffCitizenLongtailProperties {

    /**
     * {@code stub} — deterministic behaviour for Maestro/CI without downstreams.<br>
     * {@code live} — call VITO / credential-verification where wired (see {@link zw.gov.mohcc.impilo.experience.service.citizen.CitizenLongtailService}).
     */
    private Mode mode = Mode.stub;

    /**
     * When {@link #mode} is {@code live} and a downstream call fails: fall back to stub responses
     * or surface {@code 502 Bad Gateway} to the mobile client.
     */
    private FailurePolicy failurePolicy = FailurePolicy.stub_fallback;

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

    public enum Mode {
        stub,
        live
    }

    public enum FailurePolicy {
        stub_fallback,
        propagate
    }
}
