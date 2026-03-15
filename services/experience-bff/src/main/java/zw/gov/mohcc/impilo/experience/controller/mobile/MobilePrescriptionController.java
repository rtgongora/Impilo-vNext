package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * Mobile prescription endpoints.
 * POST /internal/v1/mobile/provider/prescriptions              - create prescription
 * GET  /internal/v1/mobile/provider/prescriptions?encounter_id= or patient_id= - list
 * POST /internal/v1/mobile/provider/prescriptions/{id}/cancel  - cancel prescription
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/prescriptions")
public class MobilePrescriptionController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobilePrescriptionController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record CreatePrescriptionRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String medication_name,
            String medication_code,
            @NotBlank String dosage,
            @NotBlank String frequency,
            @NotBlank String route,
            @NotNull Integer duration_days,
            String instructions,
            Integer quantity
    ) {}

    public record CancelPrescriptionRequest(
            String reason
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createPrescription(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePrescriptionRequest request) {

        UUID prescriptionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO prescriptions
                (id, tenant_id, encounter_id, patient_id, medication_name, medication_code,
                 dosage, frequency, route, duration_days, instructions, quantity,
                 status, prescribed_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
            """,
                prescriptionId, tenantId, request.encounter_id(), request.patient_id(),
                request.medication_name(), request.medication_code(),
                request.dosage(), request.frequency(), request.route(),
                request.duration_days(), request.instructions(), request.quantity(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.prescription.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Prescription",
                prescriptionId.toString(),
                Map.of(
                        "prescription_id", prescriptionId.toString(),
                        "encounter_id", request.encounter_id(),
                        "patient_id", request.patient_id(),
                        "medication_name", request.medication_name(),
                        "status", "ACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("medication_name", request.medication_name());
        attributes.put("medication_code", request.medication_code());
        attributes.put("dosage", request.dosage());
        attributes.put("frequency", request.frequency());
        attributes.put("route", request.route());
        attributes.put("duration_days", request.duration_days());
        attributes.put("instructions", request.instructions());
        attributes.put("quantity", request.quantity());
        attributes.put("status", "ACTIVE");
        attributes.put("prescribed_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", prescriptionId.toString(),
                "type", "Prescription",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPrescriptions(
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
                SELECT id, encounter_id, patient_id, medication_name, medication_code,
                       dosage, frequency, route, duration_days, instructions, quantity,
                       status, prescribed_at, cancelled_at, cancel_reason, created_at, updated_at
                FROM prescriptions
                WHERE tenant_id = ? AND encounter_id = ?::uuid
                ORDER BY prescribed_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, encounterId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM prescriptions WHERE tenant_id = ? AND encounter_id = ?::uuid
                """, Long.class, tenantId, encounterId);
        } else if (patientId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, encounter_id, patient_id, medication_name, medication_code,
                       dosage, frequency, route, duration_days, instructions, quantity,
                       status, prescribed_at, cancelled_at, cancel_reason, created_at, updated_at
                FROM prescriptions
                WHERE tenant_id = ? AND patient_id = ?::uuid
                ORDER BY prescribed_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, patientId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM prescriptions WHERE tenant_id = ? AND patient_id = ?::uuid
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

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelPrescription(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CancelPrescriptionRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        String reason = request != null && request.reason() != null ? request.reason() : "Cancelled by provider";

        int updated = jdbcTemplate.update("""
            UPDATE prescriptions
            SET status = 'CANCELLED', cancelled_at = ?, cancel_reason = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
            """, now, reason, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Active prescription not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.prescription.cancelled.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Prescription",
                id.toString(),
                Map.of(
                        "prescription_id", id.toString(),
                        "status", "CANCELLED",
                        "cancel_reason", reason
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, medication_name, medication_code,
                   dosage, frequency, route, duration_days, instructions, quantity,
                   status, prescribed_at, cancelled_at, cancel_reason, created_at, updated_at
            FROM prescriptions
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
        attributes.put("medication_name", row.get("medication_name"));
        attributes.put("medication_code", row.get("medication_code"));
        attributes.put("dosage", row.get("dosage"));
        attributes.put("frequency", row.get("frequency"));
        attributes.put("route", row.get("route"));
        attributes.put("duration_days", row.get("duration_days"));
        attributes.put("instructions", row.get("instructions"));
        attributes.put("quantity", row.get("quantity"));
        attributes.put("status", row.get("status"));
        attributes.put("prescribed_at", row.get("prescribed_at"));
        attributes.put("cancelled_at", row.get("cancelled_at"));
        attributes.put("cancel_reason", row.get("cancel_reason"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Prescription");
        resource.put("attributes", attributes);
        return resource;
    }
}
