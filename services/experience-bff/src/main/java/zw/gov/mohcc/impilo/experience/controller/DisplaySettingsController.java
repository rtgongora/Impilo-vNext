package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataGovernanceServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Display settings — theme, font size, and layout density per actor.
 *
 * <p>Persisted by the data-governance sovereign service (the per-user display-preference
 * store, alongside privacy display prefs). The BFF is a stateless proxy — it no longer
 * echoes (G048). An unset preference returns safe defaults flagged {@code persisted=false}.</p>
 */
@RestController
@RequestMapping("/internal/v1/settings/display")
public class DisplaySettingsController {

    private final DataGovernanceServiceClient dataGovernanceClient;

    public DisplaySettingsController(DataGovernanceServiceClient dataGovernanceClient) {
        this.dataGovernanceClient = dataGovernanceClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDisplaySettings(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        JsonNode persisted = dataGovernanceClient.getDisplaySettings(actorId);

        Map<String, Object> attributes = new LinkedHashMap<>();
        if (persisted == null) {
            attributes.put("theme", "system");
            attributes.put("fontSize", "medium");
            attributes.put("density", "comfortable");
            attributes.put("persisted", false);
        } else {
            attributes.put("theme", text(persisted, "theme", "system"));
            attributes.put("fontSize", text(persisted, "fontSize", "medium"));
            attributes.put("density", text(persisted, "density", "comfortable"));
            attributes.put("persisted", true);
        }
        return ResponseEntity.ok(envelope(actorId, attributes, requestId, correlationId));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateDisplaySettings(
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
        if (body.get("theme") != null) request.put("theme", body.get("theme").toString());
        if (body.get("fontSize") != null) request.put("fontSize", body.get("fontSize").toString());
        if (body.get("density") != null) request.put("density", body.get("density").toString());

        // Persisted authoritatively (and value-clamped) by the sovereign service.
        JsonNode persisted = dataGovernanceClient.updateDisplaySettings(request, "displaysettings-" + actorId);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("theme", text(persisted, "theme", "system"));
        attributes.put("fontSize", text(persisted, "fontSize", "medium"));
        attributes.put("density", text(persisted, "density", "comfortable"));
        attributes.put("persisted", true);
        return ResponseEntity.ok(envelope(actorId, attributes, requestId, correlationId));
    }

    private static Map<String, Object> envelope(String actorId, Map<String, Object> attributes,
                                                String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "display_settings", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return fallback;
        return node.get(field).asText();
    }
}
