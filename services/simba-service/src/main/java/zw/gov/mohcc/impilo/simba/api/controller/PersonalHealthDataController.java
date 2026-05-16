package zw.gov.mohcc.impilo.simba.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/personal-data")
public class PersonalHealthDataController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PersonalHealthDataController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> listSources(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT source_id AS id, person_cpid, source_type, source_name, source_device_id, source_app_id,
                       source_priority, status, provider_access_allowed, clinical_writeback_allowed,
                       consent_status, sharing_scope, category_permissions, created_at, updated_at
                FROM simba.connected_sources
                WHERE tenant_id = ? AND person_cpid = ?
                ORDER BY status DESC, source_priority ASC, updated_at DESC
                """,
                tenantId, personCpid);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/sources")
    @Transactional
    public ResponseEntity<Map<String, Object>> connectSource(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        UUID sourceId = UUID.randomUUID();
        String personCpid = required(body, "person_cpid");
        jdbc.update(
                """
                INSERT INTO simba.connected_sources (
                    source_id, tenant_id, person_cpid, source_type, source_name, source_device_id, source_app_id,
                    source_priority, status, provider_access_allowed, clinical_writeback_allowed, consent_status,
                    sharing_scope, category_permissions
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                sourceId,
                tenantId,
                personCpid,
                stringOr(body, "source_type", "unknown"),
                stringOr(body, "source_name", "Unnamed Source"),
                stringOrNull(body, "source_device_id"),
                stringOrNull(body, "source_app_id"),
                integerOr(body, "source_priority", 100),
                stringOr(body, "status", "CONNECTED"),
                booleanOr(body, "provider_access_allowed", false),
                booleanOr(body, "clinical_writeback_allowed", false),
                stringOr(body, "consent_status", "GRANTED"),
                stringOr(body, "sharing_scope", "PERSONAL_ONLY"),
                asJson(body.getOrDefault("category_permissions", Map.of())));
        recordAudit(tenantId, personCpid, sourceId, actorId, actorType, purposeOfUse, "source.connected", correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", sourceId)));
    }

    @PatchMapping("/sources/{id}/permissions")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateSourcePermissions(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("id") UUID sourceId,
            @RequestBody Map<String, Object> body) {
        int updated = jdbc.update(
                """
                UPDATE simba.connected_sources
                SET provider_access_allowed = COALESCE(?, provider_access_allowed),
                    clinical_writeback_allowed = COALESCE(?, clinical_writeback_allowed),
                    consent_status = COALESCE(?, consent_status),
                    sharing_scope = COALESCE(?, sharing_scope),
                    category_permissions = COALESCE(?::jsonb, category_permissions),
                    status = COALESCE(?, status),
                    updated_at = NOW()
                WHERE tenant_id = ? AND source_id = ?
                """,
                body.containsKey("provider_access_allowed") ? booleanOr(body, "provider_access_allowed", false) : null,
                body.containsKey("clinical_writeback_allowed") ? booleanOr(body, "clinical_writeback_allowed", false) : null,
                body.containsKey("consent_status") ? stringOr(body, "consent_status", null) : null,
                body.containsKey("sharing_scope") ? stringOr(body, "sharing_scope", null) : null,
                body.containsKey("category_permissions") ? asJson(body.get("category_permissions")) : null,
                body.containsKey("status") ? stringOr(body, "status", null) : null,
                tenantId,
                sourceId);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "source_not_found"));
        }
        String personCpid = jdbc.queryForObject(
                "SELECT person_cpid FROM simba.connected_sources WHERE tenant_id = ? AND source_id = ?",
                String.class, tenantId, sourceId);
        recordAudit(tenantId, personCpid, sourceId, actorId, actorType, purposeOfUse, "source.permission.updated", correlationId, requestId);
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/readings/manual")
    @Transactional
    public ResponseEntity<Map<String, Object>> createManualReading(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        UUID entryId = UUID.randomUUID();
        String personCpid = required(body, "person_cpid");
        jdbc.update(
                """
                INSERT INTO simba.vitals_log (entry_id, tenant_id, person_cpid, vital_type, value_numeric, unit, source, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?::timestamptz, NOW()))
                """,
                entryId,
                tenantId,
                personCpid,
                required(body, "category"),
                decimal(body.get("value")),
                stringOr(body, "unit", ""),
                stringOr(body, "source_type", "manual_entry"),
                stringOr(body, "measured_at", null));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", entryId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return ResponseEntity.ok(Map.of("data", summaryData(tenantId, personCpid, false)));
    }

    @GetMapping("/provider-summary")
    public ResponseEntity<Map<String, Object>> providerSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return ResponseEntity.ok(Map.of("data", summaryData(tenantId, personCpid, true)));
    }

    @GetMapping("/remote-alerts")
    public ResponseEntity<Map<String, Object>> listRemoteAlerts(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(value = "person_cpid", required = false) String personCpid,
            @RequestParam(value = "status", required = false) String status) {
        String sql = "SELECT alert_id AS id, person_cpid, source_id, category, vital_type, measured_at, observed_value, threshold_min, threshold_max, unit, status, severity, escalation_status, review_notes, reviewed_by_provider, reviewed_at, created_at FROM simba.remote_alerts WHERE tenant_id = ?";
        if (personCpid != null && !personCpid.isBlank()) {
            sql += " AND person_cpid = ?";
        }
        if (status != null && !status.isBlank()) {
            sql += " AND status = ?";
        }
        sql += " ORDER BY created_at DESC LIMIT 200";
        List<Map<String, Object>> rows;
        if (personCpid != null && !personCpid.isBlank() && status != null && !status.isBlank()) {
            rows = jdbc.queryForList(sql, tenantId, personCpid, status);
        } else if (personCpid != null && !personCpid.isBlank()) {
            rows = jdbc.queryForList(sql, tenantId, personCpid);
        } else if (status != null && !status.isBlank()) {
            rows = jdbc.queryForList(sql, tenantId, status);
        } else {
            rows = jdbc.queryForList(sql, tenantId);
        }
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/remote-alerts")
    @Transactional
    public ResponseEntity<Map<String, Object>> createRemoteAlert(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        UUID alertId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO simba.remote_alerts (
                    alert_id, tenant_id, person_cpid, source_id, category, vital_type, measured_at, observed_value,
                    threshold_min, threshold_max, unit, status, severity, escalation_status
                ) VALUES (?, ?, ?, ?, ?, ?, COALESCE(?::timestamptz, NOW()), ?, ?, ?, ?, ?, ?, ?)
                """,
                alertId,
                tenantId,
                required(body, "person_cpid"),
                uuidOrNull(body.get("source_id")),
                stringOr(body, "category", "vital"),
                stringOr(body, "vital_type", null),
                stringOr(body, "measured_at", null),
                decimalOrNull(body.get("observed_value")),
                decimalOrNull(body.get("threshold_min")),
                decimalOrNull(body.get("threshold_max")),
                stringOr(body, "unit", ""),
                stringOr(body, "status", "OPEN"),
                stringOr(body, "severity", "MEDIUM"),
                stringOr(body, "escalation_status", "PENDING_REVIEW"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", alertId)));
    }

    @PostMapping("/remote-alerts/{id}/review")
    @Transactional
    public ResponseEntity<Map<String, Object>> reviewRemoteAlert(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @PathVariable("id") UUID alertId,
            @RequestBody Map<String, Object> body) {
        int updated = jdbc.update(
                """
                UPDATE simba.remote_alerts
                SET status = COALESCE(?, status),
                    escalation_status = COALESCE(?, escalation_status),
                    review_notes = COALESCE(?, review_notes),
                    reviewed_by_provider = COALESCE(?, reviewed_by_provider),
                    reviewed_at = NOW(),
                    updated_at = NOW()
                WHERE tenant_id = ? AND alert_id = ?
                """,
                stringOr(body, "status", null),
                stringOr(body, "escalation_status", null),
                stringOr(body, "review_notes", null),
                providerId,
                tenantId,
                alertId);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "alert_not_found"));
        }
        return ResponseEntity.ok(Map.of("reviewed", true));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "bad_request", "message", e.getMessage()));
    }

    private Map<String, Object> summaryData(UUID tenantId, String personCpid, boolean providerView) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> sources = jdbc.queryForList(
                """
                SELECT source_id AS id, source_type, source_name, source_device_id, status, provider_access_allowed,
                       clinical_writeback_allowed, consent_status, sharing_scope, updated_at
                FROM simba.connected_sources
                WHERE tenant_id = ? AND person_cpid = ?
                ORDER BY source_priority ASC, updated_at DESC
                """,
                tenantId, personCpid);
        List<Map<String, Object>> vitals = jdbc.queryForList(
                """
                SELECT vital_type, value_numeric AS value, unit, recorded_at, source
                FROM simba.vitals_log
                WHERE tenant_id = ? AND person_cpid = ?
                ORDER BY recorded_at DESC
                LIMIT 50
                """,
                tenantId, personCpid);
        List<Map<String, Object>> activities = jdbc.queryForList(
                """
                SELECT activity_type, title, duration_minutes, calories, distance_meters, steps, source, recorded_at
                FROM simba.activities
                WHERE tenant_id = ? AND person_cpid = ?
                ORDER BY recorded_at DESC
                LIMIT 30
                """,
                tenantId, personCpid);
        List<Map<String, Object>> alerts = jdbc.queryForList(
                """
                SELECT alert_id AS id, category, vital_type, observed_value, threshold_min, threshold_max, unit, status, severity, escalation_status, created_at
                FROM simba.remote_alerts
                WHERE tenant_id = ? AND person_cpid = ?
                ORDER BY created_at DESC
                LIMIT 20
                """,
                tenantId, personCpid);
        summary.put("person_cpid", personCpid);
        summary.put("sources", sources);
        summary.put("recent_vitals", vitals);
        summary.put("activity", activities);
        summary.put("remote_alerts", alerts);
        summary.put("provider_view", providerView);
        summary.put("clinical_record_note", "Personal wellness data is not part of clinical record unless explicitly reviewed and accepted.");
        return summary;
    }

    private void recordAudit(
            UUID tenantId,
            String personCpid,
            UUID sourceId,
            String actorId,
            String actorType,
            String purposeOfUse,
            String action,
            String correlationId,
            String requestId) {
        jdbc.update(
                """
                INSERT INTO simba.source_access_audit (
                    audit_id, tenant_id, person_cpid, source_id, actor_id, actor_type, purpose_of_use, action, correlation_id, request_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                personCpid,
                sourceId,
                actorId,
                actorType,
                purposeOfUse,
                action,
                correlationId,
                requestId);
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return String.valueOf(v);
    }

    private static String stringOr(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return fallback;
        }
        return String.valueOf(v);
    }

    private static String stringOrNull(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v);
    }

    private static Integer integerOr(Map<String, Object> body, String key, int fallback) {
        Object v = body.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static Boolean booleanOr(Map<String, Object> body, String key, boolean fallback) {
        Object v = body.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing value");
        }
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal decimalOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static UUID uuidOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }
}
