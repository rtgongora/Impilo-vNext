package zw.gov.mohcc.impilo.mvumo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 client-credentials used when the inbound HTTP request has no {@code Authorization}
 * header (e.g. async/outbox follow-up) so outbound {@code tshepo-consent-service} calls still authenticate.
 */
@ConfigurationProperties(prefix = "mvumo.tshepo-consent.client-credentials")
public class MvumoTshepoClientCredentialsProperties {

    /**
     * When true and a token can be obtained, sets {@code Authorization: Bearer} on Tshepo calls if not already set.
     */
    private boolean enabled = false;
    private String tokenUri;
    private String clientId;
    private String clientSecret;
    private String scope = "openid profile";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isConfigured() {
        return tokenUri != null
                && !tokenUri.isBlank()
                && clientId != null
                && !clientId.isBlank()
                && clientSecret != null
                && !clientSecret.isBlank();
    }
}
