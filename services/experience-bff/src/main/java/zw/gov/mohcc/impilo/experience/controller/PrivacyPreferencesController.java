package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataGovernanceServiceClient;

import java.util.*;

/**
 * Privacy display preferences — per-user settings for PII visibility,
 * auto-lock timeout, and screen-share protection.
 *
 * <p>The experience-bff is a stateless composition layer with no datasource, so
 * it proxies to the Data Governance sovereign service (Privacy-by-Architecture §6),
 * which persists the preference and emits an update event. When the user has not yet
 * set a preference, safe defaults are returned (and {@code persisted=false}).</p>
 *
 * <ul>
 *   <li>GET  /internal/v1/settings/privacy — read current preferences</li>
 *   <li>PUT  /internal/v1/settings/privacy — update preferences (persisted)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/settings/privacy")
public class PrivacyPreferencesController {

    private static final String DEFAULT_LEVEL = "PARTIAL";
    private static final int DEFAULT_LOCK_MINUTES = 5;

    private final DataGovernanceServiceClient dataGovernanceClient;

    public PrivacyPreferencesController(DataGovernanceServiceClient dataGovernanceClient) {
        this.dataGovernanceClient = dataGovernanceClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPreferences(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        JsonNode persisted = dataGovernanceClient.getPrivacyPreference(actorId);

        Map<String, Object> attributes = new LinkedHashMap<>();
        if (persisted == null) {
            // No preference set yet — honest safe defaults, flagged as not persisted.
            attributes.put("defaultLevel", DEFAULT_LEVEL);
            attributes.put("autoLockMinutes", DEFAULT_LOCK_MINUTES);
            attributes.put("screenShareMode", true);
            attributes.put("persisted", false);
        } else {
            attributes.put("defaultLevel", text(persisted, "defaultLevel", DEFAULT_LEVEL));
            attributes.put("autoLockMinutes", intVal(persisted, "autoLockMinutes", DEFAULT_LOCK_MINUTES));
            attributes.put("screenShareMode", boolVal(persisted, "screenShareMode", true));
            attributes.put("persisted", true);
        }

        return ResponseEntity.ok(envelope(actorId, attributes, requestId, correlationId));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("userId", actorId);
        if (body.get("defaultLevel") != null) {
            request.put("defaultLevel", body.get("defaultLevel").toString());
        }
        if (body.get("autoLockMinutes") instanceof Number n) {
            request.put("autoLockMinutes", n.intValue());
        }
        if (body.get("screenShareMode") != null) {
            request.put("screenShareMode", Boolean.TRUE.equals(body.get("screenShareMode")));
        }

        // Persisted authoritatively (and value-clamped) by the sovereign service.
        JsonNode persisted = dataGovernanceClient.updatePrivacyPreference(
                request, "privpref-" + actorId);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("defaultLevel", text(persisted, "defaultLevel", DEFAULT_LEVEL));
        attributes.put("autoLockMinutes", intVal(persisted, "autoLockMinutes", DEFAULT_LOCK_MINUTES));
        attributes.put("screenShareMode", boolVal(persisted, "screenShareMode", true));
        attributes.put("persisted", true);

        return ResponseEntity.ok(envelope(actorId, attributes, requestId, correlationId));
    }

    private static Map<String, Object> envelope(String actorId, Map<String, Object> attributes,
                                                String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "privacy_preference",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return fallback;
        return node.get(field).asText();
    }

    private static int intVal(JsonNode node, String field, int fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return fallback;
        return node.get(field).asInt(fallback);
    }

    private static boolean boolVal(JsonNode node, String field, boolean fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return fallback;
        return node.get(field).asBoolean(fallback);
    }
}
