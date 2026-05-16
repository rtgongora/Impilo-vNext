package zw.gov.mohcc.impilo.wellness.personaldata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native personal health data layer for source governance, manual wellness entry, and
 * remote-monitoring alert triage. This controller keeps wellness truth in wellness-service
 * and exposes Simba-safe summary endpoints.
 */
@RestController
@RequestMapping("/internal/v1/wellness/personal-data")
public class WellnessPersonalDataController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WellnessPersonalDataController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> listSources(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT * FROM wellness_connected_sources
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY status DESC, source_priority ASC, updated_at DESC
                """,
                tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/sources")
    @Transactional
    public ResponseEntity<Map<String, Object>> connectSource(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String patientId = str(body, "patientId");
        jdbc.update(
                """
                INSERT INTO wellness_connected_sources (
                    id, tenant_id, patient_id, source_type, source_name, source_device_id, source_app_id,
                    source_priority, status, provider_access_allowed, clinical_writeback_allowed, consent_status,
                    sharing_scope, category_permissions
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                id,
                tenantId,
                patientId,
                strOr(body, "sourceType", "unknown"),
                strOr(body, "sourceName", "Unnamed Source"),
                strOrNull(body, "sourceDeviceId"),
                strOrNull(body, "sourceAppId"),
                intOr(body, "sourcePriority", 100),
                strOr(body, "status", "CONNECTED"),
                boolOr(body, "providerAccessAllowed", false),
                boolOr(body, "clinicalWritebackAllowed", false),
                strOr(body, "consentStatus", "GRANTED"),
                strOr(body, "sharingScope", "PERSONAL_ONLY"),
                asJson(body.getOrDefault("categoryPermissions", Map.of())));
        recordAudit(tenantId, patientId, id, actorId, actorType, purposeOfUse, "source.connected", correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PatchMapping("/sources/{id}/permissions")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateSourcePermissions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        int updated = jdbc.update(
                """
                UPDATE wellness_connected_sources
                SET provider_access_allowed = COALESCE(?, provider_access_allowed),
                    clinical_writeback_allowed = COALESCE(?, clinical_writeback_allowed),
                    consent_status = COALESCE(?, consent_status),
                    sharing_scope = COALESCE(?, sharing_scope),
                    category_permissions = COALESCE(?::jsonb, category_permissions),
                    status = COALESCE(?, status),
                    updated_at = NOW()
                WHERE tenant_id = ? AND id = ?
                """,
                body.containsKey("providerAccessAllowed") ? boolOr(body, "providerAccessAllowed", false) : null,
                body.containsKey("clinicalWritebackAllowed") ? boolOr(body, "clinicalWritebackAllowed", false) : null,
                body.containsKey("consentStatus") ? strOr(body, "consentStatus", null) : null,
                body.containsKey("sharingScope") ? strOr(body, "sharingScope", null) : null,
                body.containsKey("categoryPermissions") ? asJson(body.get("categoryPermissions")) : null,
                body.containsKey("status") ? strOr(body, "status", null) : null,
                tenantId,
                id);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "source_not_found"));
        }
        List<Map<String, Object>> sourceRows = jdbc.queryForList(
                "SELECT patient_id FROM wellness_connected_sources WHERE tenant_id = ? AND id = ?",
                tenantId, id);
        String patientId = sourceRows.isEmpty() ? "unknown" : String.valueOf(sourceRows.get(0).get("patient_id"));
        recordAudit(tenantId, patientId, id, actorId, actorType, purposeOfUse, "source.permission.updated", correlationId, requestId);
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> listPermissions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, source_name, source_type, provider_access_allowed, clinical_writeback_allowed,
                       consent_status, sharing_scope, category_permissions, updated_at
                FROM wellness_connected_sources
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY updated_at DESC
                """,
                tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/readings/manual")
    @Transactional
    public ResponseEntity<Map<String, Object>> createManualReading(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String patientId = str(body, "patientId");
        String sourceType = strOr(body, "sourceType", "manual_entry").toUpperCase();
        jdbc.update(
                """
                INSERT INTO wellness_vitals_log (id, tenant_id, patient_id, vital_type, value, unit, measured_at, source, notes)
                VALUES (?, ?, ?, ?, ?, ?, COALESCE(?::timestamptz, NOW()), ?, ?)
                """,
                id,
                tenantId,
                patientId,
                str(body, "category"),
                decimal(body.get("value")),
                strOr(body, "unit", ""),
                strOr(body, "measuredAt", null),
                sourceType,
                strOr(body, "notes", ""));
        recordAudit(tenantId, patientId, null, actorId, actorType, purposeOfUse, "reading.manual.created", correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> personalSummary(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        return ResponseEntity.ok(Map.of("data", buildSummary(tenantId, patientId, false)));
    }

    @GetMapping("/provider-summary")
    public ResponseEntity<Map<String, Object>> providerSummary(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        return ResponseEntity.ok(Map.of("data", buildSummary(tenantId, patientId, true)));
    }

    @GetMapping("/remote-alerts")
    public ResponseEntity<Map<String, Object>> listRemoteAlerts(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String status) {
        String sql = """
                SELECT * FROM wellness_remote_alerts
                WHERE tenant_id = ?
                """;
        if (patientId != null && !patientId.isBlank()) {
            sql += " AND patient_id = ? ";
        }
        if (status != null && !status.isBlank()) {
            sql += " AND status = ? ";
        }
        sql += " ORDER BY created_at DESC LIMIT 200";
        List<Map<String, Object>> rows;
        if (patientId != null && !patientId.isBlank() && status != null && !status.isBlank()) {
            rows = jdbc.queryForList(sql, tenantId, patientId, status);
        } else if (patientId != null && !patientId.isBlank()) {
            rows = jdbc.queryForList(sql, tenantId, patientId);
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
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO wellness_remote_alerts (
                    id, tenant_id, patient_id, source_id, category, vital_type, measured_at, observed_value,
                    threshold_min, threshold_max, unit, status, severity, escalation_status
                ) VALUES (?, ?, ?, ?, ?, ?, COALESCE(?::timestamptz, NOW()), ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                str(body, "patientId"),
                uuidOrNull(body.get("sourceId")),
                strOr(body, "category", "vital"),
                strOr(body, "vitalType", null),
                strOr(body, "measuredAt", null),
                decimalOrNull(body.get("observedValue")),
                decimalOrNull(body.get("thresholdMin")),
                decimalOrNull(body.get("thresholdMax")),
                strOr(body, "unit", ""),
                strOr(body, "status", "OPEN"),
                strOr(body, "severity", "MEDIUM"),
                strOr(body, "escalationStatus", "PENDING_REVIEW"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @PostMapping("/remote-alerts/{id}/review")
    @Transactional
    public ResponseEntity<Map<String, Object>> reviewRemoteAlert(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Provider-ID", required = false) String providerId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        int updated = jdbc.update(
                """
                UPDATE wellness_remote_alerts
                SET status = COALESCE(?, status),
                    escalation_status = COALESCE(?, escalation_status),
                    review_notes = COALESCE(?, review_notes),
                    reviewed_by_provider = COALESCE(?, reviewed_by_provider),
                    reviewed_at = NOW(),
                    updated_at = NOW()
                WHERE tenant_id = ? AND id = ?
                """,
                strOr(body, "status", null),
                strOr(body, "escalationStatus", null),
                strOr(body, "reviewNotes", null),
                providerId,
                tenantId,
                id);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "alert_not_found"));
        }
        return ResponseEntity.ok(Map.of("reviewed", true));
    }

    private Map<String, Object> buildSummary(String tenantId, String patientId, boolean providerView) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> sourceRows = jdbc.queryForList(
                """
                SELECT id, source_type, source_name, source_device_id, status, provider_access_allowed,
                       clinical_writeback_allowed, consent_status, sharing_scope, updated_at
                FROM wellness_connected_sources
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY source_priority ASC, updated_at DESC
                """,
                tenantId, patientId);
        List<Map<String, Object>> vitals = jdbc.queryForList(
                """
                SELECT vital_type, value, unit, measured_at, source
                FROM wellness_vitals_log
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY measured_at DESC
                LIMIT 50
                """,
                tenantId, patientId);
        List<Map<String, Object>> activity = jdbc.queryForList(
                """
                SELECT activity_date, steps, active_minutes, distance_km, sleep_hours, water_ml, calories_burned
                FROM wellness_activities
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY activity_date DESC
                LIMIT 30
                """,
                tenantId, patientId);
        List<Map<String, Object>> alerts = jdbc.queryForList(
                """
                SELECT id, category, vital_type, observed_value, threshold_min, threshold_max, unit,
                       status, severity, escalation_status, created_at
                FROM wellness_remote_alerts
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """,
                tenantId, patientId);
        summary.put("patientId", patientId);
        summary.put("sources", sourceRows);
        summary.put("recentVitals", vitals);
        summary.put("activity", activity);
        summary.put("remoteAlerts", alerts);
        summary.put("providerView", providerView);
        summary.put("clinicalRecordNote", "Personal wellness data is not part of clinical record unless reviewed/accepted.");
        return summary;
    }

    private void recordAudit(
            String tenantId,
            String patientId,
            UUID sourceId,
            String actorId,
            String actorType,
            String purposeOfUse,
            String action,
            String correlationId,
            String requestId) {
        jdbc.update(
                """
                INSERT INTO wellness_source_access_audit (
                    id, tenant_id, patient_id, source_id, actor_id, actor_type, purpose_of_use,
                    action, correlation_id, request_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                patientId,
                sourceId,
                actorId,
                actorType,
                purposeOfUse,
                action,
                correlationId,
                requestId,
                OffsetDateTime.now());
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(v);
    }

    private static String strOr(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return fallback;
        }
        return String.valueOf(v);
    }

    private static String strOrNull(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v);
    }

    private static boolean boolOr(Map<String, Object> body, String key, boolean fallback) {
        Object v = body.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static int intOr(Map<String, Object> body, String key, int fallback) {
        Object v = body.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", ex.getMessage() != null ? ex.getMessage() : "Invalid request"));
    }
}
