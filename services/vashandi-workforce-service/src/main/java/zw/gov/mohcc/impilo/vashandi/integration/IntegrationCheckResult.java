package zw.gov.mohcc.impilo.vashandi.integration;

import java.util.Map;
import java.util.Optional;

public record IntegrationCheckResult(
        String dependency,
        String status,
        String message,
        Optional<Map<String, Object>> payload
) {
    public static final String LIVE = "live";
    public static final String DEGRADED = "pending_backend";
    public static final String UNAVAILABLE = "unavailable";

    public static IntegrationCheckResult live(String dependency, Map<String, Object> payload) {
        return new IntegrationCheckResult(dependency, LIVE, "ok", Optional.ofNullable(payload));
    }

    public static IntegrationCheckResult degraded(String dependency, String message) {
        return new IntegrationCheckResult(dependency, DEGRADED, message, Optional.empty());
    }

    public boolean isLive() {
        return LIVE.equals(status);
    }
}
