package zw.gov.mohcc.impilo.shared.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This workload's own service credential.
 *
 * <p>Deliberately per-workload, not estate-wide. The trust doctrine requires that each service
 * authenticate as itself with a dedicated credential; a shared client would make every callee
 * unable to tell one caller from another, which is the defect this replaces rather than repeats.
 * Until per-workload Keycloak clients exist for a given service, leaving {@code client-id} unset
 * means the provider mints nothing and the caller sends no Authorization header.</p>
 */
@ConfigurationProperties(prefix = "impilo.s2s.token")
public class WorkloadTokenProperties {

    /** Off unless a workload has been given its own credential. */
    private boolean enabled = false;
    private String tokenUri = "";
    private String clientId = "";
    private String clientSecret = "";
    private String scope = "";
    /** Refresh this far before expiry, so a token is never used at the boundary. */
    private int refreshSkewSeconds = 30;
    /** Upper bound of the random additional earliness, to de-synchronise fleet-wide refresh. */
    private int refreshJitterSeconds = 20;
    /** Used when the token endpoint omits expires_in. */
    private long defaultLifetimeSeconds = 300;
    /** Do not re-hammer a failing identity provider on every in-flight request. */
    private int failureBackoffSeconds = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTokenUri() { return tokenUri; }
    public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public int getRefreshSkewSeconds() { return refreshSkewSeconds; }
    public void setRefreshSkewSeconds(int refreshSkewSeconds) { this.refreshSkewSeconds = refreshSkewSeconds; }
    public int getRefreshJitterSeconds() { return refreshJitterSeconds; }
    public void setRefreshJitterSeconds(int refreshJitterSeconds) { this.refreshJitterSeconds = refreshJitterSeconds; }
    public long getDefaultLifetimeSeconds() { return defaultLifetimeSeconds; }
    public void setDefaultLifetimeSeconds(long defaultLifetimeSeconds) { this.defaultLifetimeSeconds = defaultLifetimeSeconds; }
    public int getFailureBackoffSeconds() { return failureBackoffSeconds; }
    public void setFailureBackoffSeconds(int failureBackoffSeconds) { this.failureBackoffSeconds = failureBackoffSeconds; }
}
