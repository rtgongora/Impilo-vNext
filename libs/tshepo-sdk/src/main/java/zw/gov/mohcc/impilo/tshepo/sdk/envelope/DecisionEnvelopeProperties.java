package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * How a service treats the PDP's signed decision envelope (Phase D, D3).
 *
 * <p>Configured under {@code impilo.trust.decision-envelope}.</p>
 */
@ConfigurationProperties(prefix = "impilo.trust.decision-envelope")
public class DecisionEnvelopeProperties {

    public enum Mode {
        /** Do not look at the header. The state every service starts in. */
        OFF,
        /** Verify and log; never reject. The soak step before ENFORCE. */
        SHADOW,
        /** Verify and reject what does not verify. */
        ENFORCE
    }

    private Mode mode = Mode.OFF;

    /** Where to fetch the PDP's public keys. Required once mode is not OFF. */
    private String jwksUri = "http://tshepo-keys-service:8184/v1/keys/jwks";

    /** Matches the 60s the trust verifier already tolerates elsewhere. */
    private int clockSkewSeconds = 60;

    /** How long a fetched key set is reused before a scheduled refresh. */
    private int jwksCacheSeconds = 300;

    /**
     * Minimum gap between refreshes triggered by an unrecognised {@code kid}. Without it,
     * a caller can force a JWKS fetch per request by inventing a kid each time, which turns
     * signature verification into an outbound request amplifier pointed at keys-service.
     */
    private int jwksRefreshCooldownSeconds = 30;

    /**
     * Paths exempt from the envelope requirement, matched by prefix. Probes and metrics are
     * not routed through the gateway, so they never carry an envelope and would otherwise
     * fail the moment ENFORCE is switched on — taking the readiness probe with them, which
     * is how an enforcement flag becomes a rolling restart loop.
     */
    private List<String> exemptPathPrefixes = new ArrayList<>(List.of(
            "/actuator", "/health", "/internal/v1/health", "/v3/api-docs", "/swagger-ui"));

    /**
     * Also require the envelope's path claim to match the request path.
     *
     * <p>Off by default because it is not universally true. The deployed Helm gateway routes
     * without rewriting, so the service sees the path the PDP saw — but the local dev gateway
     * uses {@code prefix_rewrite} on many routes, and there the two legitimately differ.
     * Enabling this where paths are rewritten rejects every request, so it is opt-in per
     * deployment rather than a default that works on one gateway and not the other.</p>
     */
    private boolean bindPath = false;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }
    public int getClockSkewSeconds() { return clockSkewSeconds; }
    public void setClockSkewSeconds(int clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }
    public int getJwksCacheSeconds() { return jwksCacheSeconds; }
    public void setJwksCacheSeconds(int jwksCacheSeconds) { this.jwksCacheSeconds = jwksCacheSeconds; }
    public int getJwksRefreshCooldownSeconds() { return jwksRefreshCooldownSeconds; }
    public void setJwksRefreshCooldownSeconds(int s) { this.jwksRefreshCooldownSeconds = s; }
    public List<String> getExemptPathPrefixes() { return exemptPathPrefixes; }
    public void setExemptPathPrefixes(List<String> exemptPathPrefixes) { this.exemptPathPrefixes = exemptPathPrefixes; }
    public boolean isBindPath() { return bindPath; }
    public void setBindPath(boolean bindPath) { this.bindPath = bindPath; }
}
