package zw.gov.mohcc.impilo.ubomi.integration.rg;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Config surface for the Registrar-General (RG / CRVS) outbound adapter,
 * exactly as specified in {@code docs/design/rg-crvs-outbound-adapter.md}.
 *
 * <pre>
 * ubomi:
 *   rg:
 *     enabled: false          # flag-gated; default off everywhere
 *     base-url: ""            # must be on the allow-list below
 *     allowed-hosts: []       # startup-validated endpoint allow-list
 *     mtls:
 *       key-ref: ""           # tshepo-keys custody refs, never inline PEM
 *       truststore-ref: ""
 *     connect-timeout-ms: 2000
 *     read-timeout-ms: 5000
 *     pending-ttl-hours: 72
 * </pre>
 *
 * <p>The properties bean is always present (harmless when disabled); the actual
 * HTTP client bean is only created when {@link #isEnabled()} is true.</p>
 */
@ConfigurationProperties(prefix = "ubomi.rg")
public class UbomiRgProperties {

    /** Master flag. Default OFF everywhere — no RG HTTP client bean exists when false. */
    private boolean enabled = false;

    /** Base URL of the RG API. Its host must appear in {@link #allowedHosts}. */
    private String baseUrl = "";

    /** Startup-validated endpoint allow-list. A base-url host not on it is a startup error. */
    private List<String> allowedHosts = new ArrayList<>();

    private final Mtls mtls = new Mtls();

    /** Connect timeout — kept short so an RG outage fails fast into RG_PENDING. */
    private int connectTimeoutMs = 2000;

    /** Read timeout — kept short so an RG outage fails fast into RG_PENDING. */
    private int readTimeoutMs = 5000;

    /** RG_PENDING attempts older than this expire to RG_UNAVAILABLE. */
    private int pendingTtlHours = 72;

    public static class Mtls {
        /** tshepo-keys custody ref for the client key/cert. Never inline PEM. */
        private String keyRef = "";
        /** tshepo-keys custody ref for the RG truststore. Never inline PEM. */
        private String truststoreRef = "";

        public String getKeyRef() { return keyRef; }
        public void setKeyRef(String keyRef) { this.keyRef = keyRef; }
        public String getTruststoreRef() { return truststoreRef; }
        public void setTruststoreRef(String truststoreRef) { this.truststoreRef = truststoreRef; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public List<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
    public Mtls getMtls() { return mtls; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getPendingTtlHours() { return pendingTtlHours; }
    public void setPendingTtlHours(int pendingTtlHours) { this.pendingTtlHours = pendingTtlHours; }
}
