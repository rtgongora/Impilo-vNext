package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen coverage/insurance endpoints.
 * GET /internal/v1/mobile/citizen/coverage
 * GET /internal/v1/mobile/citizen/coverage/{id}
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/coverage")
public class CitizenCoverageController {

    private final JdbcTemplate jdbcTemplate;

    public CitizenCoverageController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        UUID patientId = resolvePatientId(tenantId, actorId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, plan_name, plan_type, member_id, status, effective_from, effective_to,
                   copay, deductible, out_of_pocket_max, currency
            FROM coverage_plans WHERE tenant_id = ? AND patient_id = ?
            ORDER BY effective_from DESC
            """, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, plan_name, plan_type, member_id, status, effective_from, effective_to,
                   copay, deductible, out_of_pocket_max, currency
            FROM coverage_plans WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Coverage plan not found: " + id);

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
        r.put("planName", row.get("plan_name"));
        r.put("planType", row.get("plan_type"));
        r.put("memberId", row.get("member_id"));
        r.put("status", row.get("status"));
        r.put("effectiveFrom", row.get("effective_from"));
        r.put("effectiveTo", row.get("effective_to"));
        r.put("copay", row.get("copay"));
        r.put("deductible", row.get("deductible"));
        r.put("outOfPocketMax", row.get("out_of_pocket_max"));
        r.put("currency", row.get("currency"));
        return r;
    }
}
