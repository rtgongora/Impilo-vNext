package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen lab results endpoints.
 * GET /internal/v1/mobile/citizen/results
 * GET /internal/v1/mobile/citizen/results/{id}
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/results")
public class CitizenResultsController {

    private final JdbcTemplate jdbcTemplate;

    public CitizenResultsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
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
            SELECT id, test_name, category, status, value, unit, reference_range,
                   interpretation, ordered_by, facility_name, collected_at, result_at, created_at
            FROM lab_results WHERE tenant_id = ? AND patient_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));

        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lab_results WHERE tenant_id = ? AND patient_id = ?" +
                        (status != null ? " AND status = '" + status + "'" : ""),
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, test_name, category, status, value, unit, reference_range,
                   interpretation, ordered_by, facility_name, collected_at, result_at, created_at
            FROM lab_results WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Lab result not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
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
        r.put("testName", row.get("test_name"));
        r.put("category", row.get("category"));
        r.put("status", row.get("status"));
        r.put("value", row.get("value"));
        r.put("unit", row.get("unit"));
        r.put("referenceRange", row.get("reference_range"));
        r.put("interpretation", row.get("interpretation"));
        r.put("orderedBy", row.get("ordered_by"));
        r.put("facilityName", row.get("facility_name"));
        r.put("collectedAt", row.get("collected_at"));
        r.put("resultAt", row.get("result_at"));
        r.put("createdAt", row.get("created_at"));
        return r;
    }
}
