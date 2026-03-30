package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Extracts the inner payload from either a v1.1 EventEnvelope or a raw legacy payload.
 *
 * <p>DUAL emit mode publishes two messages per outbox event:</p>
 * <ul>
 *   <li>Legacy topic — raw domain payload JSON</li>
 *   <li>V1.1 topic  — EventEnvelope JSON with {@code payload} field containing domain data</li>
 * </ul>
 *
 * <p>Consumers call {@link #extract(String, ObjectMapper)} and always get a flat
 * domain-data map regardless of which format arrived.</p>
 */
public final class EventPayloadExtractor {

    private static final Logger log = LoggerFactory.getLogger(EventPayloadExtractor.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private EventPayloadExtractor() {}

    /**
     * Parse {@code message} and return the domain payload map.
     *
     * <p>Detection logic:</p>
     * <ol>
     *   <li>If the root JSON has an {@code event_id} or {@code eventId} field →
     *       treat as v1.1 EventEnvelope; extract the {@code payload} field.</li>
     *   <li>Otherwise treat as raw legacy payload.</li>
     * </ol>
     *
     * @return extracted payload map, or {@code null} if parsing fails
     */
    public static Map<String, Object> extract(String message, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(message);

            boolean isEnvelope = root.has("event_id") || root.has("eventId");

            if (isEnvelope) {
                JsonNode payloadNode = root.path("payload");
                if (payloadNode.isMissingNode() || payloadNode.isNull()) {
                    log.warn("EventEnvelope has no payload field, skipping");
                    return null;
                }
                return mapper.convertValue(payloadNode, MAP_TYPE);
            } else {
                return mapper.convertValue(root, MAP_TYPE);
            }
        } catch (Exception e) {
            log.error("Failed to parse event message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safely read a String value from a payload map, checking both snake_case and camelCase keys.
     */
    public static String str(Map<String, Object> payload, String snakeKey, String camelKey) {
        Object v = payload.get(snakeKey);
        if (v == null) v = payload.get(camelKey);
        return v != null ? v.toString() : null;
    }

    public static String str(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Unwrap nested {@code full} map from delta events (IDENTITY_VERIFIED, IDENTITY_DECEASED).
     * Returns the full map if present, otherwise returns the original payload.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> unwrapFull(Map<String, Object> payload) {
        Object full = payload.get("full");
        if (full instanceof Map) {
            return (Map<String, Object>) full;
        }
        return payload;
    }
}
