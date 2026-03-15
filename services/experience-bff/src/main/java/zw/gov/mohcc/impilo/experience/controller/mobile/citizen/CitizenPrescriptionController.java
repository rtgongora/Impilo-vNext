package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

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
 * Citizen prescription and refill endpoints.
 * GET  /internal/v1/mobile/citizen/prescriptions
 * GET  /internal/v1/mobile/citizen/prescriptions/{id}
 * POST /internal/v1/mobile/citizen/prescriptions/{id}/refill
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/prescriptions")
public class CitizenPrescriptionController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenPrescriptionController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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
            SELECT p.id, p.medication_name, p.dosage, p.frequency, p.duration,
                   p.quantity, p.status, p.prescribed_by, p.dispensed_at, p.created_at
            FROM prescriptions p
            WHERE p.tenant_id = ? AND p.patient_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));

        if (status != null) {
            sql.append(" AND p.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY p.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prescriptions WHERE tenant_id = ? AND patient_id = ?" +
                        (status != null ? " AND status = '" + status + "'" : ""),
                Long.class, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, medication_name, dosage, frequency, duration, quantity, status,
                   prescribed_by, dispensed_at, created_at
            FROM prescriptions WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Prescription not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/refill")
    @Transactional
    public ResponseEntity<Map<String, Object>> refill(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        UUID refillId = UUID.randomUUID();

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.refill-requested.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Prescription", id.toString(),
                Map.of("prescription_id", id.toString(), "refill_id", refillId.toString(), "status", "PENDING"),
                Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refillId", refillId.toString());
        data.put("status", "PENDING");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
        r.put("medicationName", row.get("medication_name"));
        r.put("dosage", row.get("dosage"));
        r.put("frequency", row.get("frequency"));
        r.put("duration", row.get("duration"));
        r.put("quantity", row.get("quantity"));
        r.put("status", row.get("status"));
        r.put("prescribedBy", row.get("prescribed_by"));
        r.put("facilityName", "");
        r.put("refillsRemaining", 3);
        r.put("lastFilledAt", row.get("dispensed_at"));
        r.put("createdAt", row.get("created_at"));
        return r;
    }
}
