package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Omnichannel controller — callbacks, channel configs, disclosure rules,
 * SMS journeys, USSD menus, IVR flows.
 */
@RestController
@RequestMapping("/internal/v1/omnichannel")
public class OmnichannelController {

    private final JdbcTemplate jdbcTemplate;

    public OmnichannelController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Callbacks ────────────────────────────────────────────────

    @GetMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> listCallbacks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status) {
        String sql = status != null
            ? "SELECT * FROM omni_callback_queue WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT 50"
            : "SELECT * FROM omni_callback_queue WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 50";
        List<Map<String, Object>> rows = status != null
            ? jdbcTemplate.queryForList(sql, tenantId, status)
            : jdbcTemplate.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows, "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/callbacks")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCallback(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO omni_callback_queue (id, tenant_id, channel, caller_id, caller_name, reason, priority, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)""",
            id, tenantId, body.getOrDefault("channel", "PHONE"), body.get("callerId"),
            body.get("callerName"), body.get("reason"), body.getOrDefault("priority", "NORMAL"), now, now);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id.toString(), "status", "PENDING"), "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/callbacks/{id}/complete")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeCallback(
            @PathVariable UUID id, @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        jdbcTemplate.update("UPDATE omni_callback_queue SET status = 'COMPLETED', completed_at = ?, notes = ?, updated_at = ? WHERE id = ? AND tenant_id = ?",
            OffsetDateTime.now(), notes, OffsetDateTime.now(), id, tenantId);
        return ResponseEntity.ok(Map.of("data", Map.of("id", id.toString(), "status", "COMPLETED"), "meta", Map.of("request_id", requestId)));
    }

    // ── Channel Configs ──────────────────────────────────────────

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM omni_channel_configs WHERE tenant_id = ? AND is_active = true ORDER BY channel_type", tenantId);
        return ResponseEntity.ok(Map.of("data", rows, "meta", Map.of("request_id", requestId)));
    }

    // ── SMS Journeys ─────────────────────────────────────────────

    @GetMapping("/sms-journeys")
    public ResponseEntity<Map<String, Object>> listSmsJourneys(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM omni_sms_journeys WHERE tenant_id = ? ORDER BY created_at DESC", tenantId);
        return ResponseEntity.ok(Map.of("data", rows, "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/sms-journeys")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSmsJourney(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO omni_sms_journeys (id, tenant_id, name, trigger_event, message_template, schedule_cron, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""",
            id, tenantId, body.get("name"), body.get("triggerEvent"), body.get("messageTemplate"), body.get("scheduleCron"), OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id.toString()), "meta", Map.of("request_id", requestId)));
    }

    // ── USSD Menus ───────────────────────────────────────────────

    @GetMapping("/ussd-menus")
    public ResponseEntity<Map<String, Object>> listUssdMenus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM omni_ussd_menus WHERE tenant_id = ? ORDER BY created_at DESC", tenantId);
        return ResponseEntity.ok(Map.of("data", rows, "meta", Map.of("request_id", requestId)));
    }

    // ── IVR Flows ────────────────────────────────────────────────

    @GetMapping("/ivr-flows")
    public ResponseEntity<Map<String, Object>> listIvrFlows(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM omni_ivr_flows WHERE tenant_id = ? ORDER BY created_at DESC", tenantId);
        return ResponseEntity.ok(Map.of("data", rows, "meta", Map.of("request_id", requestId)));
    }

    // ── Disclosure Rules ─────────────────────────────────────────

    @GetMapping("/disclosure-rules")
    public ResponseEntity<Map<String, Object>> listDisclosureRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM omni_disclosure_rules WHERE tenant_id = ? AND is_active = true ORDER BY channel_type", tenantId);
        return ResponseEntity.ok(Map.of("data", rows, "meta", Map.of("request_id", requestId)));
    }

    @PostMapping("/disclosure-rules")
    @Transactional
    public ResponseEntity<Map<String, Object>> createDisclosureRule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO omni_disclosure_rules (id, tenant_id, channel_type, data_category, disclosure_level, created_at)
            VALUES (?, ?, ?, ?, ?, ?)""",
            id, tenantId, body.get("channelType"), body.get("dataCategory"), body.getOrDefault("disclosureLevel", "MINIMAL"), OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id.toString()), "meta", Map.of("request_id", requestId)));
    }
}
