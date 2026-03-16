package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Condition;
import zw.gov.mohcc.impilo.experience.repository.ConditionRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Condition management endpoints.
 * GET  /internal/v1/conditions?patient_id= — list conditions for patient (paged).
 * POST /internal/v1/conditions — create condition.
 * POST /internal/v1/conditions/{id}/resolve — resolve condition.
 */
@RestController
@RequestMapping("/internal/v1/conditions")
public class ConditionsController {

    private final ConditionRepository conditionRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public ConditionsController(ConditionRepository conditionRepository,
                                OutboxService outboxService,
                                JdbcTemplate jdbcTemplate) {
        this.conditionRepository = conditionRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateConditionRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String condition_name,
            String icd_code,
            String category,
            String clinical_status,
            String severity,
            LocalDate onset_date,
            String recorded_by,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listConditions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<Condition> result;
        if (patientId != null) {
            result = conditionRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = conditionRepository.findAll(pageable);
        }

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createCondition(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateConditionRequest request) {

        UUID conditionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String clinicalStatus = request.clinical_status() != null ? request.clinical_status() : "ACTIVE";

        jdbcTemplate.update("""
            INSERT INTO conditions
                (id, tenant_id, patient_id, encounter_id, condition_name,
                 icd_code, category, clinical_status, severity, onset_date,
                 recorded_by, notes, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                conditionId, tenantId, request.patient_id(), request.encounter_id(),
                request.condition_name(),
                request.icd_code(), request.category(), clinicalStatus,
                request.severity(), request.onset_date(),
                request.recorded_by(), request.notes(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.condition.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Condition",
                conditionId.toString(),
                Map.of(
                        "condition_id", conditionId.toString(),
                        "patient_id", request.patient_id(),
                        "condition_name", request.condition_name(),
                        "icd_code", request.icd_code() != null ? request.icd_code() : "",
                        "clinical_status", clinicalStatus
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("condition_name", request.condition_name());
        attributes.put("icd_code", request.icd_code());
        attributes.put("category", request.category());
        attributes.put("clinical_status", clinicalStatus);
        attributes.put("severity", request.severity());
        attributes.put("onset_date", request.onset_date());
        attributes.put("recorded_by", request.recorded_by());
        attributes.put("notes", request.notes());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", conditionId.toString(),
                "type", "Condition",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<Map<String, Object>> resolveCondition(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Condition condition = conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition not found: " + id));

        condition.resolve();
        conditionRepository.save(condition);

        outboxService.writeOutboxEvent(
                "impilo.experience.condition.resolved.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Condition",
                id.toString(),
                Map.of(
                        "condition_id", id.toString(),
                        "patient_id", condition.getPatientId().toString(),
                        "clinical_status", "RESOLVED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(condition));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Condition c) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", c.getPatientId());
        attributes.put("encounter_id", c.getEncounterId());
        attributes.put("condition_name", c.getConditionName());
        attributes.put("icd_code", c.getIcdCode());
        attributes.put("category", c.getCategory());
        attributes.put("clinical_status", c.getClinicalStatus());
        attributes.put("severity", c.getSeverity());
        attributes.put("onset_date", c.getOnsetDate());
        attributes.put("abatement_date", c.getAbatementDate());
        attributes.put("recorded_by", c.getRecordedBy());
        attributes.put("notes", c.getNotes());
        attributes.put("created_at", c.getCreatedAt());
        attributes.put("updated_at", c.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", c.getId().toString());
        resource.put("type", "Condition");
        resource.put("attributes", attributes);
        return resource;
    }
}
