package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Dev/preview-only Product Owner full-access mode for confirming Administration &amp; Governance surfaces.
 * Off by default; refused in production environments.
 */
@ConfigurationProperties(prefix = "impilo.preview.product-owner-access")
public class ProductOwnerAccessProperties {

    private boolean enabled = false;
    private String allowedActors = "b0000000-0000-4000-8000-000000000010,superadmin@impilo.gov.zw";
    private String blockedEnvironments = "production,prod";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAllowedActors(String allowedActors) {
        this.allowedActors = allowedActors;
    }

    public void setBlockedEnvironments(String blockedEnvironments) {
        this.blockedEnvironments = blockedEnvironments;
    }

    public boolean isEffective(String deploymentEnvironment) {
        if (!enabled) {
            return false;
        }
        if (deploymentEnvironment == null || deploymentEnvironment.isBlank()) {
            return true;
        }
        String normalized = deploymentEnvironment.trim().toLowerCase(Locale.ROOT);
        return parseList(blockedEnvironments).stream()
                .noneMatch(blocked -> blocked.equals(normalized));
    }

    public boolean isAllowlisted(String actorId, String actorEmail) {
        List<String> allowed = parseList(allowedActors);
        if (allowed.isEmpty()) {
            return false;
        }
        if (actorId != null && !actorId.isBlank()) {
            String id = actorId.trim();
            if (allowed.stream().anyMatch(a -> a.equalsIgnoreCase(id))) {
                return true;
            }
        }
        if (actorEmail != null && !actorEmail.isBlank()) {
            String email = actorEmail.trim().toLowerCase(Locale.ROOT);
            return allowed.stream().anyMatch(a -> a.equalsIgnoreCase(email));
        }
        return false;
    }

    private static List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
