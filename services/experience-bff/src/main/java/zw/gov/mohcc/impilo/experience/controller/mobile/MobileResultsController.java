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
 * Mobile provider lab results endpoints.
 * GET  /internal/v1/mobile/provider/labs/results              - list resulted lab orders for facility
 * POST /internal/v1/mobile/provider/labs/results/{id}/acknowledge - acknowledge a result
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/labs/results")
public class MobileResultsController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileResultsController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listResultedOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        // Look up the provider's facility
        List<Map<String, Object>> providerRows = jdbcTemplate.queryForList(
                "SELECT facility_id FROM registry_providers WHERE tenant_id = ? AND user_id = ?",
                tenantId, actorId);

        if (providerRows.isEmpty()) {
            throw new ResourceNotFoundException("Provider not found for: " + actorId);
        }

        Object facilityId = providerRows.get(0).get("facility_id");
        if (facilityId == null) {
            throw new ResourceNotFoundException("Provider has no assigned facility");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT lo.id, lo.encounter_id, lo.patient_id, lo.test_code, lo.test_name,
                   lo.priority, lo.clinical_notes, lo.specimen_type, lo.status,
                   lo.result_value, lo.result_unit, lo.result_reference_range,
                   lo.result_interpretation, lo.ordered_at, lo.collected_at,
                   lo.resulted_at, lo.created_at, lo.updated_at
            FROM lab_orders lo
            JOIN encounters e ON e.id = lo.encounter_id AND e.tenant_id = lo.tenant_id
            WHERE lo.tenant_id = ? AND e.facility_id = ? AND lo.status = 'RESULTED'
            ORDER BY lo.resulted_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, facilityId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM lab_orders lo
            JOIN encounters e ON e.id = lo.encounter_id AND e.tenant_id = lo.tenant_id
            WHERE lo.tenant_id = ? AND e.facility_id = ? AND lo.status = 'RESULTED'
            """, Long.class, tenantId, facilityId);

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

    @PostMapping("/{id}/acknowledge")
    @Transactional
    public ResponseEntity<Map<String, Object>> acknowledgeResult(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader("X-Actor-ID") String actorId) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE lab_orders
            SET status = 'ACKNOWLEDGED', updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'RESULTED'
            """, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Resulted lab order not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.lab_result.acknowledged.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "LabOrder",
                id.toString(),
                Map.of(
                        "lab_order_id", id.toString(),
                        "acknowledged_by", actorId,
                        "status", "ACKNOWLEDGED"
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, test_code, test_name, priority,
                   clinical_notes, specimen_type, status, result_value, result_unit,
                   result_reference_range, result_interpretation, ordered_at,
                   collected_at, resulted_at, created_at, updated_at
            FROM lab_orders
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
        attributes.put("test_code", row.get("test_code"));
        attributes.put("test_name", row.get("test_name"));
        attributes.put("priority", row.get("priority"));
        attributes.put("clinical_notes", row.get("clinical_notes"));
        attributes.put("specimen_type", row.get("specimen_type"));
        attributes.put("status", row.get("status"));
        attributes.put("result_value", row.get("result_value"));
        attributes.put("result_unit", row.get("result_unit"));
        attributes.put("result_reference_range", row.get("result_reference_range"));
        attributes.put("result_interpretation", row.get("result_interpretation"));
        attributes.put("ordered_at", row.get("ordered_at"));
        attributes.put("collected_at", row.get("collected_at"));
        attributes.put("resulted_at", row.get("resulted_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "LabResult");
        resource.put("attributes", attributes);
        return resource;
    }
}
