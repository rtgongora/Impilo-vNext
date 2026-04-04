package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen lab results endpoints — queries the canonical lab_orders table
 * for RESULTED orders with result_data (V12).
 *
 * <p>Replaces the previous implementation that queried a non-existent
 * lab_results table. Results are now sourced from lab_orders WHERE
 * status = 'RESULTED', with structured result_data JSONB.</p>
 *
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, test_name, test_code, category, priority, status,
                   clinical_notes, ordered_by_name, facility_id,
                   result_data, result_notes, resulted_by_name,
                   collected_at, resulted_at, created_at
            FROM lab_orders
            WHERE tenant_id = ? AND patient_id = ? AND status IN ('RESULTED', 'REVIEWED')
            ORDER BY resulted_at DESC NULLS LAST
            LIMIT ? OFFSET ?
            """, tenantId, patientId, limit, offset);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lab_orders WHERE tenant_id = ? AND patient_id = ? AND status IN ('RESULTED', 'REVIEWED')",
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
            SELECT id, test_name, test_code, category, priority, status,
                   clinical_notes, ordered_by_name, facility_id,
                   result_data, result_notes, resulted_by_name,
                   collected_at, resulted_at, created_at
            FROM lab_orders WHERE id = ? AND tenant_id = ?
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
        r.put("testCode", row.get("test_code"));
        r.put("category", row.get("category"));
        r.put("status", row.get("status"));
        r.put("resultData", row.get("result_data"));
        r.put("resultNotes", row.get("result_notes"));
        r.put("orderedBy", row.get("ordered_by_name"));
        r.put("resultedBy", row.get("resulted_by_name"));
        r.put("collectedAt", row.get("collected_at"));
        r.put("resultedAt", row.get("resulted_at"));
        r.put("createdAt", row.get("created_at"));
        return r;
    }
}
