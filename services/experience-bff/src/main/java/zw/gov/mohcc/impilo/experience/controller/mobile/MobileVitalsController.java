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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile vitals endpoints.
 * POST   /internal/v1/mobile/provider/vitals           - record single vital
 * POST   /internal/v1/mobile/provider/vitals/batch      - record multiple vitals
 * GET    /internal/v1/mobile/provider/vitals?encounter_id= - vitals for encounter
 * GET    /internal/v1/mobile/provider/vitals/latest?patient_id= - latest vitals for patient
 * DELETE /internal/v1/mobile/provider/vitals/{id}       - delete a vital
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/vitals")
public class MobileVitalsController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileVitalsController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record RecordVitalRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String vital_type,
            @NotNull BigDecimal value,
            String unit,
            String notes
    ) {}

    public record BatchVitalsRequest(
            @NotNull List<RecordVitalRequest> vitals
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> recordVital(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordVitalRequest request) {

        UUID vitalId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO vitals
                (id, tenant_id, encounter_id, patient_id, vital_type, value, unit, notes, recorded_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
            """,
                vitalId, tenantId, request.encounter_id(), request.patient_id(),
                request.vital_type(), request.value(), request.unit(), request.notes(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.vital.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Vital",
                vitalId.toString(),
                Map.of(
                        "vital_id", vitalId.toString(),
                        "encounter_id", request.encounter_id(),
                        "patient_id", request.patient_id(),
                        "vital_type", request.vital_type(),
                        "value", request.value().toString()
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("vital_type", request.vital_type());
        attributes.put("value", request.value());
        attributes.put("unit", request.unit());
        attributes.put("notes", request.notes());
        attributes.put("recorded_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", vitalId.toString(),
                "type", "Vital",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/batch")
    @Transactional
    public ResponseEntity<Map<String, Object>> recordVitalsBatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody BatchVitalsRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> createdVitals = new ArrayList<>();

        for (RecordVitalRequest vital : request.vitals()) {
            UUID vitalId = UUID.randomUUID();

            jdbcTemplate.update("""
                INSERT INTO vitals
                    (id, tenant_id, encounter_id, patient_id, vital_type, value, unit, notes, recorded_at, created_at, updated_at)
                VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
                """,
                    vitalId, tenantId, vital.encounter_id(), vital.patient_id(),
                    vital.vital_type(), vital.value(), vital.unit(), vital.notes(),
                    now, now, now);

            outboxService.writeOutboxEvent(
                    "impilo.experience.vital.recorded.v1",
                    correlationId,
                    requestId,
                    (idempotencyKey != null ? idempotencyKey : requestId) + ":" + vitalId,
                    tenantId,
                    podId,
                    "Vital",
                    vitalId.toString(),
                    Map.of(
                            "vital_id", vitalId.toString(),
                            "encounter_id", vital.encounter_id(),
                            "patient_id", vital.patient_id(),
                            "vital_type", vital.vital_type(),
                            "value", vital.value().toString()
                    ),
                    Map.of()
            );

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("encounter_id", vital.encounter_id());
            attributes.put("patient_id", vital.patient_id());
            attributes.put("vital_type", vital.vital_type());
            attributes.put("value", vital.value());
            attributes.put("unit", vital.unit());
            attributes.put("notes", vital.notes());
            attributes.put("recorded_at", now);
            attributes.put("created_at", now);

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", vitalId.toString());
            resource.put("type", "Vital");
            resource.put("attributes", attributes);
            createdVitals.add(resource);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", createdVitals);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "count", createdVitals.size()
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "encounter_id") String encounterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, vital_type, value, unit, notes,
                   recorded_at, created_at, updated_at
            FROM vitals
            WHERE tenant_id = ? AND encounter_id = ?::uuid
            ORDER BY recorded_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, encounterId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM vitals WHERE tenant_id = ? AND encounter_id = ?::uuid
            """, Long.class, tenantId, encounterId);

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

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latestVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT DISTINCT ON (vital_type)
                   id, encounter_id, patient_id, vital_type, value, unit, notes,
                   recorded_at, created_at, updated_at
            FROM vitals
            WHERE tenant_id = ? AND patient_id = ?::uuid
            ORDER BY vital_type, recorded_at DESC
            """, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteVital(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        int deleted = jdbcTemplate.update("""
            DELETE FROM vitals WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (deleted == 0) {
            throw new ResourceNotFoundException("Vital not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.vital.deleted.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Vital",
                id.toString(),
                Map.of("vital_id", id.toString()),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "deleted", true));
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
        attributes.put("vital_type", row.get("vital_type"));
        attributes.put("value", row.get("value"));
        attributes.put("unit", row.get("unit"));
        attributes.put("notes", row.get("notes"));
        attributes.put("recorded_at", row.get("recorded_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Vital");
        resource.put("attributes", attributes);
        return resource;
    }
}
