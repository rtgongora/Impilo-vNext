package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Privacy display preferences — per-user settings for PII visibility,
 * auto-lock timeout, and screen-share protection.
 *
 * <p>Serves the {@code privacy_display_preference} table (V39 migration).
 * The frontend reads these preferences on session start and uses them
 * to configure the privacy display store and inactivity lock timeout.</p>
 *
 * <ul>
 *   <li>GET  /internal/v1/settings/privacy — read current preferences</li>
 *   <li>PUT  /internal/v1/settings/privacy — update preferences</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/settings/privacy")
public class PrivacyPreferencesController {

    private final JdbcTemplate jdbcTemplate;

    public PrivacyPreferencesController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, default_level, auto_lock_minutes, screen_share_mode " +
                "FROM privacy_display_preference WHERE tenant_id = ? AND user_id = ?",
                tenantId, actorId);

        Map<String, Object> attributes;
        if (rows.isEmpty()) {
            // Return defaults if no preference has been saved yet
            attributes = Map.of(
                    "defaultLevel", "PARTIAL",
                    "autoLockMinutes", 5,
                    "screenShareMode", true);
        } else {
            Map<String, Object> row = rows.get(0);
            attributes = Map.of(
                    "defaultLevel", row.get("default_level"),
                    "autoLockMinutes", row.get("auto_lock_minutes"),
                    "screenShareMode", row.get("screen_share_mode"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "privacy_preference",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Transactional
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

        String defaultLevel = body.getOrDefault("defaultLevel", "PARTIAL").toString().toUpperCase();
        int autoLockMinutes = body.containsKey("autoLockMinutes")
                ? ((Number) body.get("autoLockMinutes")).intValue() : 5;
        boolean screenShareMode = body.containsKey("screenShareMode")
                ? Boolean.TRUE.equals(body.get("screenShareMode")) : true;

        // Validate
        if (!Set.of("FULL", "PARTIAL", "MINIMAL").contains(defaultLevel)) {
            defaultLevel = "PARTIAL";
        }
        if (autoLockMinutes < 1 || autoLockMinutes > 60) {
            autoLockMinutes = 5;
        }

        jdbcTemplate.update(
                "INSERT INTO privacy_display_preference (id, tenant_id, user_id, default_level, " +
                "auto_lock_minutes, screen_share_mode, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (tenant_id, user_id) " +
                "DO UPDATE SET default_level = ?, auto_lock_minutes = ?, screen_share_mode = ?, updated_at = NOW()",
                tenantId, actorId, defaultLevel, autoLockMinutes, screenShareMode,
                defaultLevel, autoLockMinutes, screenShareMode);

        Map<String, Object> attributes = Map.of(
                "defaultLevel", defaultLevel,
                "autoLockMinutes", autoLockMinutes,
                "screenShareMode", screenShareMode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "privacy_preference",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
