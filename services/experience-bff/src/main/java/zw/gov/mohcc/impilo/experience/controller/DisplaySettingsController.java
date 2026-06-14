package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Display preferences — theme, font size, and layout density per actor.
 *
 * <p>BFF-owned until a dedicated settings service exists. Values are returned
 * for the authenticated actor and echoed on update (preview-safe defaults).</p>
 */
@RestController
@RequestMapping("/internal/v1/settings/display")
public class DisplaySettingsController {

    private static final Set<String> THEMES = Set.of("light", "dark", "system");
    private static final Set<String> FONT_SIZES = Set.of("small", "medium", "large");
    private static final Set<String> DENSITIES = Set.of("comfortable", "compact");

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDisplaySettings(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "display_settings",
                "attributes", defaultAttributes()));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
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

        String theme = normalize(body.get("theme"), THEMES, "system");
        String fontSize = normalize(body.get("fontSize"), FONT_SIZES, "medium");
        String density = normalize(body.get("density"), DENSITIES, "comfortable");

        Map<String, Object> attributes = Map.of(
                "theme", theme,
                "fontSize", fontSize,
                "density", density);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "display_settings",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> defaultAttributes() {
        return Map.of(
                "theme", "system",
                "fontSize", "medium",
                "density", "comfortable");
    }

    private static String normalize(Object raw, Set<String> allowed, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim().toLowerCase();
        return allowed.contains(value) ? value : fallback;
    }
}
