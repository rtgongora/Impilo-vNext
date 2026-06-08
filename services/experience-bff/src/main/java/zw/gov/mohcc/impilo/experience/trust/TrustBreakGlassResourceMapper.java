package zw.gov.mohcc.impilo.experience.trust;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes TSHEPO Authz break-glass payloads into Experience JSON:API resources.
 */
public final class TrustBreakGlassResourceMapper {

    private TrustBreakGlassResourceMapper() {}

    public static List<Map<String, Object>> toReviewResources(JsonNode upstream) {
        List<Map<String, Object>> resources = new ArrayList<>();
        if (upstream == null || upstream.isNull()) {
            return resources;
        }
        if (upstream.isArray()) {
            upstream.forEach(node -> resources.add(toReviewResource(node)));
            return resources;
        }
        resources.add(toReviewResource(upstream));
        return resources;
    }

    public static Map<String, Object> toRequestResource(JsonNode upstream) {
        if (upstream == null || upstream.isNull()) {
            return Map.of();
        }
        Map<String, Object> attributes = baseAttributes(upstream);
        attributes.put("reviewStatus", text(upstream, "reviewStatus", "review_status", "PENDING_REVIEW"));
        return Map.of(
                "id", text(upstream, "id"),
                "type", "break_glass_request",
                "attributes", attributes);
    }

    private static Map<String, Object> toReviewResource(JsonNode node) {
        Map<String, Object> attributes = baseAttributes(node);
        attributes.put("reviewStatus", text(node, "reviewStatus", "review_status", "PENDING_REVIEW"));
        attributes.put("approved", bool(node, "approved"));
        attributes.put("reviewedBy", text(node, "reviewedBy", "reviewed_by"));
        attributes.put("reviewedAt", text(node, "reviewedAt", "reviewed_at"));
        attributes.put("expiresAt", text(node, "expiresAt", "expires_at"));
        attributes.put("grantedAt", text(node, "grantedAt", "granted_at", "timestamp"));
        attributes.put("timestamp", attributes.get("grantedAt"));
        attributes.put("patientId", text(node, "resourceId", "resource_id", "patientId", "patient_id"));
        attributes.put("duration", text(node, "duration"));
        attributes.put("actorName", text(node, "actorName", "actor_name", "actorId", "actor_id"));
        return Map.of(
                "id", text(node, "id"),
                "type", "break_glass_review",
                "attributes", attributes);
    }

    private static Map<String, Object> baseAttributes(JsonNode node) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("eventId", text(node, "id"));
        attributes.put("actorId", text(node, "actorId", "actor_id"));
        attributes.put("reason", text(node, "reason"));
        attributes.put("resourceType", text(node, "resourceType", "resource_type"));
        attributes.put("resourceId", text(node, "resourceId", "resource_id"));
        return attributes;
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String value = node.get(key).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static boolean bool(JsonNode node, String key) {
        return node.has(key) && node.get(key).asBoolean(false);
    }
}
