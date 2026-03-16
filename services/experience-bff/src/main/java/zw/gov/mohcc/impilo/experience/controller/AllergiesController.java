package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Allergy;
import zw.gov.mohcc.impilo.experience.repository.AllergyRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Allergy management endpoints.
 * GET    /internal/v1/allergies?patient_id= — list allergies for patient.
 * POST   /internal/v1/allergies — create allergy.
 * DELETE /internal/v1/allergies/{id} — mark allergy INACTIVE.
 */
@RestController
@RequestMapping("/internal/v1/allergies")
public class AllergiesController {

    private final AllergyRepository allergyRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public AllergiesController(AllergyRepository allergyRepository,
                               OutboxService outboxService,
                               JdbcTemplate jdbcTemplate) {
        this.allergyRepository = allergyRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateAllergyRequest(
            @NotBlank String patient_id,
            @NotBlank String allergen,
            String allergen_type,
            String reaction,
            String severity,
            LocalDate onset_date,
            String recorded_by
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAllergies(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        List<Allergy> records;
        if (patientId != null) {
            records = allergyRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId));
        } else {
            records = allergyRepository.findAll();
        }

        List<Map<String, Object>> data = records.stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createAllergy(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateAllergyRequest request) {

        UUID allergyId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO allergies
                (id, tenant_id, patient_id, allergen, allergen_type,
                 reaction, severity, status, onset_date, recorded_by,
                 created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            """,
                allergyId, tenantId, request.patient_id(),
                request.allergen(), request.allergen_type(),
                request.reaction(), request.severity() != null ? request.severity() : "UNKNOWN",
                request.onset_date(),
                request.recorded_by(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.allergy.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Allergy",
                allergyId.toString(),
                Map.of(
                        "allergy_id", allergyId.toString(),
                        "patient_id", request.patient_id(),
                        "allergen", request.allergen(),
                        "severity", request.severity() != null ? request.severity() : "UNKNOWN",
                        "status", "ACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("allergen", request.allergen());
        attributes.put("allergen_type", request.allergen_type());
        attributes.put("reaction", request.reaction());
        attributes.put("severity", request.severity() != null ? request.severity() : "UNKNOWN");
        attributes.put("status", "ACTIVE");
        attributes.put("onset_date", request.onset_date());
        attributes.put("recorded_by", request.recorded_by());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", allergyId.toString(),
                "type", "Allergy",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deactivateAllergy(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Allergy allergy = allergyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allergy not found: " + id));

        jdbcTemplate.update("""
            UPDATE allergies SET status = 'INACTIVE', updated_at = ? WHERE id = ?
            """, OffsetDateTime.now(), id);

        outboxService.writeOutboxEvent(
                "impilo.experience.allergy.deactivated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Allergy",
                id.toString(),
                Map.of(
                        "allergy_id", id.toString(),
                        "patient_id", allergy.getPatientId().toString(),
                        "status", "INACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", id.toString(),
                "type", "Allergy",
                "status", "INACTIVE"
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Allergy a) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", a.getPatientId());
        attributes.put("allergen", a.getAllergen());
        attributes.put("allergen_type", a.getAllergenType());
        attributes.put("reaction", a.getReaction());
        attributes.put("severity", a.getSeverity());
        attributes.put("status", a.getStatus());
        attributes.put("onset_date", a.getOnsetDate());
        attributes.put("recorded_by", a.getRecordedBy());
        attributes.put("verified_at", a.getVerifiedAt());
        attributes.put("created_at", a.getCreatedAt());
        attributes.put("updated_at", a.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", a.getId().toString());
        resource.put("type", "Allergy");
        resource.put("attributes", attributes);
        return resource;
    }
}
