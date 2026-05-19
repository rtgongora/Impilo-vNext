package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps integration-hub raw route/adapter health into the canonical mobile
 * status enum consumed by {@code @impilo/mobile-integration} and rendered by
 * {@code IntegrationStatusBadge}.
 *
 * <p>This is deliberately a thin pure-function helper so it can be reused
 * symmetrically by the provider-facing controller and unit-tested without
 * Spring context.</p>
 *
 * <p>Citizen and provider views differ only in which fields are exposed:
 * citizens never see vendor names or correlation IDs, providers do.</p>
 */
public final class IntegrationStatusMapper {

    private IntegrationStatusMapper() {}

    public static List<Map<String, Object>> mapForCitizen(JsonNode body) {
        return mapList(body, false);
    }

    public static List<Map<String, Object>> mapForProvider(JsonNode body) {
        return mapList(body, true);
    }

    public static Map<String, Object> mapOneForCitizen(JsonNode node) {
        return mapOne(node, false);
    }

    public static Map<String, Object> mapOneForProvider(JsonNode node) {
        return mapOne(node, true);
    }

    private static List<Map<String, Object>> mapList(JsonNode body, boolean privileged) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (body == null) return result;
        JsonNode array = body.isArray() ? body : body.get("data");
        if (array == null || !array.isArray()) return result;
        for (JsonNode node : array) {
            result.add(mapOne(node, privileged));
        }
        return result;
    }

    private static Map<String, Object> mapOne(JsonNode node, boolean privileged) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (node == null || node.isNull()) return m;
        String id = text(node, "id", "route_id", "code");
        String domain = inferDomain(text(node, "domain", "type", "category", "code"));
        String enabledRaw = text(node, "enabled", "active", "status");
        boolean enabled = "true".equalsIgnoreCase(enabledRaw) || "ACTIVE".equalsIgnoreCase(enabledRaw)
                || "ENABLED".equalsIgnoreCase(enabledRaw);
        String health = text(node, "health", "lastHealth", "last_health", "state");
        String status = inferStatus(enabled, health, text(node, "status"));
        m.put("id", id);
        m.put("domain", domain);
        m.put("status", status);
        m.put("label", text(node, "displayName", "display_name", "name", "label"));
        m.put("updated_at", text(node, "lastCheckedAt", "last_checked_at", "updatedAt", "updated_at"));
        if (privileged) {
            m.put("vendor", text(node, "vendor", "adapter", "provider"));
            m.put("correlation_id", text(node, "correlationId", "correlation_id"));
            m.put("retry_count", node.has("retryCount")
                    ? node.get("retryCount").asInt(0)
                    : node.has("retry_count") ? node.get("retry_count").asInt(0) : 0);
        }
        return m;
    }

    private static String inferDomain(String raw) {
        if (raw == null) return "OTHER";
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("LAB") || upper.contains("LIMS")) return "LAB";
        if (upper.contains("IMAG") || upper.contains("PACS")) return "IMAGING";
        if (upper.contains("PAY") || upper.contains("MUSHEX") || upper.contains("CLAIM")) return "PAYMENT";
        if (upper.contains("DELIV") || upper.contains("NHUME") || upper.contains("LOGIST"))
            return "DELIVERY";
        if (upper.contains("TELE") || upper.contains("VIDEO")) return "TELEHEALTH";
        if (upper.contains("COMM") || upper.contains("SMS") || upper.contains("WHATSAPP")) return "COMMS";
        if (upper.contains("EMR") || upper.contains("HIE")) return "EMR";
        if (upper.contains("ELMIS") || upper.contains("STOCK")) return "STOCK";
        return "OTHER";
    }

    private static String inferStatus(boolean enabled, String health, String explicit) {
        if (explicit != null) {
            String u = explicit.toUpperCase(Locale.ROOT);
            if (u.equals("CONNECTED") || u.equals("AVAILABLE") || u.equals("ACTIVE")) return "CONNECTED";
            if (u.equals("PENDING") || u.equals("AWAITING")) return "PENDING";
            if (u.equals("PROCESSING")) return "PROCESSING";
            if (u.equals("FAILED")) return "FAILED";
            if (u.equals("RETRY_SCHEDULED") || u.equals("RETRYING")) return "RETRY_SCHEDULED";
            if (u.equals("ACTION_REQUIRED")) return "ACTION_REQUIRED";
            if (u.equals("UNAVAILABLE") || u.equals("TEMPORARILY_UNAVAILABLE")) return "TEMPORARILY_UNAVAILABLE";
            if (u.equals("NOT_CONFIGURED")) return "NOT_CONFIGURED";
        }
        if (!enabled) return "NOT_CONFIGURED";
        if (health == null) return "PENDING";
        String u = health.toUpperCase(Locale.ROOT);
        if (u.contains("HEALTHY") || u.contains("UP") || u.contains("OK")) return "CONNECTED";
        if (u.contains("DEGRADED") || u.contains("SLOW")) return "RETRY_SCHEDULED";
        if (u.contains("DOWN") || u.contains("ERROR") || u.contains("FAIL"))
            return "TEMPORARILY_UNAVAILABLE";
        return "PENDING";
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull() && v.isValueNode()) {
                return v.asText();
            }
        }
        return null;
    }
}
