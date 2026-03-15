package zw.gov.mohcc.impilo.experience.controller.mobile;

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
 * Mobile telemedicine session endpoints.
 * GET  /internal/v1/mobile/provider/telemedicine/sessions             - list sessions
 * POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/join   - join session
 * POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/end    - end session
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/telemedicine")
public class MobileTelemedicineController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileTelemedicineController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "provider_id") String providerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, encounter_id, patient_id, provider_id, facility_id,
                   session_type, status, room_url, scheduled_at, started_at, ended_at,
                   duration_seconds, notes, created_at, updated_at
            FROM telemedicine_sessions
            WHERE tenant_id = ?
            """);
        StringBuilder countSql = new StringBuilder(
                "SELECT count(*) FROM telemedicine_sessions WHERE tenant_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        List<Object> countParams = new ArrayList<>();
        countParams.add(tenantId);

        if (providerId != null) {
            sql.append(" AND provider_id = ?");
            countSql.append(" AND provider_id = ?");
            params.add(providerId);
            countParams.add(providerId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            countSql.append(" AND status = ?");
            params.add(status);
            countParams.add(status);
        }

        sql.append(" ORDER BY scheduled_at DESC NULLS LAST LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{id}/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE telemedicine_sessions
            SET status = 'IN_PROGRESS', started_at = COALESCE(started_at, ?), updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'IN_PROGRESS')
            """, now, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Joinable telemedicine session not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.telemedicine.joined.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "TelemedicineSession",
                id.toString(),
                Map.of(
                        "session_id", id.toString(),
                        "status", "IN_PROGRESS"
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, provider_id, facility_id,
                   session_type, status, room_url, scheduled_at, started_at, ended_at,
                   duration_seconds, notes, created_at, updated_at
            FROM telemedicine_sessions
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> sessionData = rows.isEmpty() ? new LinkedHashMap<>() : toResource(rows.get(0));
        if (!rows.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) sessionData.get("attributes");
            String token = UUID.randomUUID().toString();
            String channel = rows.get(0).get("room_url") != null
                    ? rows.get(0).get("room_url").toString()
                    : "session-" + id;
            attrs.put("token", token);
            attrs.put("channel", channel);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", sessionData);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

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
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        String notes = body != null && body.containsKey("notes") ? body.get("notes").toString() : null;

        int updated = jdbcTemplate.update("""
            UPDATE telemedicine_sessions
            SET status = 'COMPLETED', ended_at = ?,
                duration_seconds = EXTRACT(EPOCH FROM (? - COALESCE(started_at, created_at)))::int,
                notes = COALESCE(?, notes), updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'IN_PROGRESS'
            """, now, now, notes, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Active telemedicine session not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.telemedicine.ended.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "TelemedicineSession",
                id.toString(),
                Map.of(
                        "session_id", id.toString(),
                        "status", "COMPLETED"
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, provider_id, facility_id,
                   session_type, status, room_url, scheduled_at, started_at, ended_at,
                   duration_seconds, notes, created_at, updated_at
            FROM telemedicine_sessions
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows.isEmpty() ? Map.of() : toResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", row.get("encounter_id"));
        attributes.put("patient_id", row.get("patient_id"));
        attributes.put("provider_id", row.get("provider_id"));
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("session_type", row.get("session_type"));
        attributes.put("status", row.get("status"));
        attributes.put("room_url", row.get("room_url"));
        attributes.put("scheduled_at", row.get("scheduled_at"));
        attributes.put("started_at", row.get("started_at"));
        attributes.put("ended_at", row.get("ended_at"));
        attributes.put("duration_seconds", row.get("duration_seconds"));
        attributes.put("notes", row.get("notes"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "TelemedicineSession");
        resource.put("attributes", attributes);
        return resource;
    }
}
