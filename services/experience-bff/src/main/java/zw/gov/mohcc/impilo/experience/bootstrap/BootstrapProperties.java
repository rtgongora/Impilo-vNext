package zw.gov.mohcc.impilo.experience.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "impilo.bootstrap")
public class BootstrapProperties {

    private boolean enabled = true;
    private String tokenHash = "";
    private String tokenExpiresAt = "";
    private String allowedMethods = "one_time_token,signed_authorisation";
    private boolean requireMfa = true;
    private String environment = "development";
    private int bootstrapRoleTtlHours = 24;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(String tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public List<String> getAllowedMethods() {
        if (allowedMethods == null || allowedMethods.isBlank()) return List.of();
        return java.util.Arrays.stream(allowedMethods.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    public void setAllowedMethods(String allowedMethods) { this.allowedMethods = allowedMethods; }
    public boolean isRequireMfa() { return requireMfa; }
    public void setRequireMfa(boolean requireMfa) { this.requireMfa = requireMfa; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public int getBootstrapRoleTtlHours() { return bootstrapRoleTtlHours; }
    public void setBootstrapRoleTtlHours(int bootstrapRoleTtlHours) { this.bootstrapRoleTtlHours = bootstrapRoleTtlHours; }
}
