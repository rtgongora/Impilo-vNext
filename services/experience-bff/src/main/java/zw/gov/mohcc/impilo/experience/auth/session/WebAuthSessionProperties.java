package zw.gov.mohcc.impilo.experience.auth.session;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impilo.auth.web-session")
public class WebAuthSessionProperties {
    private boolean enabled;
    private boolean cookieSecure = true;
    private long sessionTtlSeconds = 28800;
    private long transactionTtlSeconds = 600;
    private String encryptionKey = "";
    private String publicIssuer = "https://impilo.mohcc.gov.zw:8480/realms/impilo";
    private String internalIssuer = "http://keycloak:8080/realms/impilo";
    private String clientId = "experience-ui";
    private String clientSecret = "";
    private String redirectUri = "https://impilo.mohcc.gov.zw/internal/v1/auth/oidc/callback";
    private String postLogoutRedirectUri = "https://impilo.mohcc.gov.zw/";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public long getSessionTtlSeconds() { return sessionTtlSeconds; }
    public void setSessionTtlSeconds(long sessionTtlSeconds) { this.sessionTtlSeconds = sessionTtlSeconds; }
    public long getTransactionTtlSeconds() { return transactionTtlSeconds; }
    public void setTransactionTtlSeconds(long transactionTtlSeconds) { this.transactionTtlSeconds = transactionTtlSeconds; }
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    public String getPublicIssuer() { return publicIssuer; }
    public void setPublicIssuer(String publicIssuer) { this.publicIssuer = publicIssuer; }
    public String getInternalIssuer() { return internalIssuer; }
    public void setInternalIssuer(String internalIssuer) { this.internalIssuer = internalIssuer; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) { this.postLogoutRedirectUri = postLogoutRedirectUri; }
}
