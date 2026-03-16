package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen health timeline endpoints.
 * GET /internal/v1/mobile/citizen/timeline
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/timeline")
public class CitizenTimelineController {

    private final JdbcTemplate jdbcTemplate;

    public CitizenTimelineController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, encounter_id, event_type, title, description,
                   source_type, source_id, actor_id, actor_name, occurred_at, created_at
            FROM clinical_timeline WHERE tenant_id = ? AND patient_id = ?
            """);
        StringBuilder countSql = new StringBuilder(
                "SELECT count(*) FROM clinical_timeline WHERE tenant_id = ? AND patient_id = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));
        List<Object> cParams = new ArrayList<>(List.of(tenantId, patientId));

        if (eventType != null) {
            sql.append(" AND event_type = ?");
            countSql.append(" AND event_type = ?");
            params.add(eventType);
            cParams.add(eventType);
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, cParams.toArray());

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();
        return ResponseEntity.ok(buildPagedResponse(data, requestId, correlationId, page, limit, total));
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
        r.put("encounterId", row.get("encounter_id") != null ? row.get("encounter_id").toString() : null);
        r.put("eventType", row.get("event_type"));
        r.put("title", row.get("title"));
        r.put("description", row.get("description"));
        r.put("sourceType", row.get("source_type"));
        r.put("sourceId", row.get("source_id") != null ? row.get("source_id").toString() : null);
        r.put("actorId", row.get("actor_id"));
        r.put("actorName", row.get("actor_name"));
        r.put("occurredAt", row.get("occurred_at"));
        r.put("createdAt", row.get("created_at"));
        return r;
    }

    private Map<String, Object> buildPagedResponse(List<Map<String, Object>> data, String requestId,
            String correlationId, int page, int size, Long total) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page, "size", size,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / size) : 0
                )
        ));
        return response;
    }
}
