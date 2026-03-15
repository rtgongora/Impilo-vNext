package zw.gov.mohcc.impilo.experience.controller.mobile;

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
 * Mobile lab order endpoints.
 * POST /internal/v1/mobile/provider/labs              - create lab order
 * GET  /internal/v1/mobile/provider/labs?encounter_id= or patient_id= - list
 * GET  /internal/v1/mobile/provider/labs/{id}         - get single
 * POST /internal/v1/mobile/provider/labs/{id}/cancel  - cancel lab order
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/labs")
public class MobileLabController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileLabController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record CreateLabOrderRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String test_code,
            @NotBlank String test_name,
            String priority,
            String clinical_notes,
            String specimen_type
    ) {}

    public record CancelLabOrderRequest(
            String reason
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createLabOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateLabOrderRequest request) {

        UUID labOrderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String priority = request.priority() != null ? request.priority() : "ROUTINE";

        jdbcTemplate.update("""
            INSERT INTO lab_orders
                (id, tenant_id, encounter_id, patient_id, test_code, test_name,
                 priority, clinical_notes, specimen_type, status, ordered_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, 'ORDERED', ?, ?, ?)
            """,
                labOrderId, tenantId, request.encounter_id(), request.patient_id(),
                request.test_code(), request.test_name(),
                priority, request.clinical_notes(), request.specimen_type(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.lab_order.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "LabOrder",
                labOrderId.toString(),
                Map.of(
                        "lab_order_id", labOrderId.toString(),
                        "encounter_id", request.encounter_id(),
                        "patient_id", request.patient_id(),
                        "test_code", request.test_code(),
                        "test_name", request.test_name(),
                        "priority", priority,
                        "status", "ORDERED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("test_code", request.test_code());
        attributes.put("test_name", request.test_name());
        attributes.put("priority", priority);
        attributes.put("clinical_notes", request.clinical_notes());
        attributes.put("specimen_type", request.specimen_type());
        attributes.put("status", "ORDERED");
        attributes.put("ordered_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", labOrderId.toString(),
                "type", "LabOrder",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        if (encounterId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, encounter_id, patient_id, test_code, test_name, priority,
                       clinical_notes, specimen_type, status, result_value, result_unit,
                       result_reference_range, result_interpretation, ordered_at,
                       collected_at, resulted_at, cancelled_at, cancel_reason, created_at, updated_at
                FROM lab_orders
                WHERE tenant_id = ? AND encounter_id = ?::uuid
                ORDER BY ordered_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, encounterId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM lab_orders WHERE tenant_id = ? AND encounter_id = ?::uuid
                """, Long.class, tenantId, encounterId);
        } else if (patientId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, encounter_id, patient_id, test_code, test_name, priority,
                       clinical_notes, specimen_type, status, result_value, result_unit,
                       result_reference_range, result_interpretation, ordered_at,
                       collected_at, resulted_at, cancelled_at, cancel_reason, created_at, updated_at
                FROM lab_orders
                WHERE tenant_id = ? AND patient_id = ?::uuid
                ORDER BY ordered_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, patientId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM lab_orders WHERE tenant_id = ? AND patient_id = ?::uuid
                """, Long.class, tenantId, patientId);
        } else {
            rows = List.of();
            total = 0L;
        }

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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, test_code, test_name, priority,
                   clinical_notes, specimen_type, status, result_value, result_unit,
                   result_reference_range, result_interpretation, ordered_at,
                   collected_at, resulted_at, cancelled_at, cancel_reason, created_at, updated_at
            FROM lab_orders
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Lab order not found: " + id);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CancelLabOrderRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        String reason = request != null && request.reason() != null ? request.reason() : "Cancelled by provider";

        int updated = jdbcTemplate.update("""
            UPDATE lab_orders
            SET status = 'CANCELLED', cancelled_at = ?, cancel_reason = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('ORDERED', 'COLLECTED')
            """, now, reason, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Cancellable lab order not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.lab_order.cancelled.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "LabOrder",
                id.toString(),
                Map.of(
                        "lab_order_id", id.toString(),
                        "status", "CANCELLED",
                        "cancel_reason", reason
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, test_code, test_name, priority,
                   clinical_notes, specimen_type, status, result_value, result_unit,
                   result_reference_range, result_interpretation, ordered_at,
                   collected_at, resulted_at, cancelled_at, cancel_reason, created_at, updated_at
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
        attributes.put("cancelled_at", row.get("cancelled_at"));
        attributes.put("cancel_reason", row.get("cancel_reason"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "LabOrder");
        resource.put("attributes", attributes);
        return resource;
    }
}
