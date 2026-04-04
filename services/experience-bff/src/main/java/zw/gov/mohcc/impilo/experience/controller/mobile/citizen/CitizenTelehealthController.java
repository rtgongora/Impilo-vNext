package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen telehealth session endpoints.
 * GET  /internal/v1/mobile/citizen/telehealth/sessions
 * GET  /internal/v1/mobile/citizen/telehealth/sessions/{id}
 * POST /internal/v1/mobile/citizen/telehealth/sessions
 * POST /internal/v1/mobile/citizen/telehealth/sessions/{id}/join
 * POST /internal/v1/mobile/citizen/telehealth/sessions/{id}/end
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/telehealth")
public class CitizenTelehealthController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenTelehealthController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record RequestTeleconsultBody(
            @NotBlank String reason,
            String preferredDate,
            String sessionType,
            String providerId,
            String referralId
    ) {}

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, provider_id, provider_name, session_type, status,
                   scheduled_at, started_at, ended_at, room_url, notes, reason, referral_id
            FROM citizen_telehealth_sessions WHERE tenant_id = ? AND patient_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));

        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY scheduled_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM citizen_telehealth_sessions WHERE tenant_id = ? AND patient_id = ?",
                Long.class, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, provider_id, provider_name, session_type, status,
                   scheduled_at, started_at, ended_at, room_url, notes, reason, referral_id
            FROM citizen_telehealth_sessions WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Telehealth session not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions")
    @Transactional
    public ResponseEntity<Map<String, Object>> requestTeleconsult(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody RequestTeleconsultBody body) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String sessionType = body.sessionType() != null ? body.sessionType() : "VIDEO";
        OffsetDateTime scheduledAt = body.preferredDate() != null
                ? OffsetDateTime.parse(body.preferredDate() + "T10:00:00Z")
                : now.plusHours(1);

        jdbcTemplate.update("""
            INSERT INTO citizen_telehealth_sessions
                (id, tenant_id, patient_id, provider_id, provider_name, session_type,
                 status, scheduled_at, reason, room_url, referral_id, created_at, updated_at)
            VALUES (?, ?, ?, ?::uuid, 'Assigned Provider', ?, 'REQUESTED', ?, ?, ?, ?::uuid, ?, ?)
            """, sessionId, tenantId, patientId,
                body.providerId(), sessionType, scheduledAt, body.reason(),
                "session-" + sessionId, body.referralId(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.teleconsult-requested.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "TelehealthSession", sessionId.toString(),
                Map.of("session_id", sessionId.toString(), "type", sessionType, "status", "REQUESTED",
                        "referral_id", body.referralId() != null ? body.referralId() : ""),
                Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", sessionId.toString());
        data.put("sessionType", sessionType);
        data.put("status", "REQUESTED");
        data.put("scheduledAt", scheduledAt);
        data.put("providerName", "Assigned Provider");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{id}/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE citizen_telehealth_sessions
            SET status = 'IN_PROGRESS', started_at = COALESCE(started_at, ?), updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'REQUESTED', 'IN_PROGRESS')
            """, now, now, id, tenantId);

        if (updated == 0) throw new ResourceNotFoundException("Joinable session not found: " + id);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.teleconsult-joined.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "TelehealthSession", id.toString(),
                Map.of("session_id", id.toString(), "status", "IN_PROGRESS"),
                Map.of());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, provider_id, provider_name, session_type, status,
                   scheduled_at, started_at, ended_at, room_url, notes, reason, referral_id
            FROM citizen_telehealth_sessions WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> session = toResource(rows.get(0));
        String token = UUID.randomUUID().toString();
        String channel = rows.get(0).get("room_url") != null ? rows.get(0).get("room_url").toString() : "session-" + id;

        Map<String, Object> attrs = new LinkedHashMap<>(session);
        attrs.put("token", token);
        attrs.put("channel", channel);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "type", "TelehealthSession", "attributes", attrs));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{id}/end")
    @Transactional
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        String notes = body != null && body.containsKey("notes") ? body.get("notes").toString() : null;

        int updated = jdbcTemplate.update("""
            UPDATE citizen_telehealth_sessions
            SET status = 'COMPLETED', ended_at = ?, notes = COALESCE(?, notes), updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'IN_PROGRESS'
            """, now, notes, now, id, tenantId);

        if (updated == 0) throw new ResourceNotFoundException("Active session not found: " + id);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.teleconsult-ended.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "TelehealthSession", id.toString(),
                Map.of("session_id", id.toString(), "status", "COMPLETED"),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "COMPLETED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient not found for: " + actorId);
        return (UUID) rows.get(0).get("id");
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("providerId", row.get("provider_id") != null ? row.get("provider_id").toString() : null);
        r.put("providerName", row.get("provider_name"));
        r.put("sessionType", row.get("session_type"));
        r.put("status", row.get("status"));
        r.put("scheduledAt", row.get("scheduled_at"));
        r.put("startedAt", row.get("started_at"));
        r.put("endedAt", row.get("ended_at"));
        r.put("roomUrl", row.get("room_url"));
        r.put("notes", row.get("notes"));
        r.put("referralId", row.get("referral_id") != null ? row.get("referral_id").toString() : null);
        return r;
    }
}
