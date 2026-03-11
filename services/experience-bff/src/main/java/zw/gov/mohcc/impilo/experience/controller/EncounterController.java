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
import zw.gov.mohcc.impilo.experience.domain.Encounter;
import zw.gov.mohcc.impilo.experience.repository.EncounterRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Encounter management endpoints.
 * GET  /internal/v1/encounters — list encounters with patient_id filter, pagination.
 * GET  /internal/v1/encounters/{id} — get single encounter.
 * POST /internal/v1/encounters — create encounter.
 * POST /internal/v1/encounters/{id}/close — close encounter.
 */
@RestController
@RequestMapping("/internal/v1/encounters")
public class EncounterController {

    private final EncounterRepository encounterRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public EncounterController(EncounterRepository encounterRepository,
                               OutboxService outboxService,
                               JdbcTemplate jdbcTemplate) {
        this.encounterRepository = encounterRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateEncounterRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            @NotBlank String encounter_type,
            String chief_complaint
    ) {}

    public record CloseEncounterRequest(
            String diagnosis
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<Encounter> result;
        if (patientId != null) {
            result = encounterRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = encounterRepository.findAll(pageable);
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Encounter encounter = encounterRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(encounter));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEncounterRequest request) {

        UUID encounterId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO encounters
                (id, tenant_id, facility_id, patient_id, encounter_type, chief_complaint,
                 status, started_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, 'IN_PROGRESS', ?, ?, ?)
            """,
                encounterId, tenantId, request.facility_id(), request.patient_id(),
                request.encounter_type(), request.chief_complaint(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Encounter",
                encounterId.toString(),
                Map.of(
                        "encounter_id", encounterId.toString(),
                        "patient_id", request.patient_id(),
                        "facility_id", request.facility_id(),
                        "encounter_type", request.encounter_type(),
                        "status", "IN_PROGRESS"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("encounter_type", request.encounter_type());
        attributes.put("chief_complaint", request.chief_complaint());
        attributes.put("status", "IN_PROGRESS");
        attributes.put("started_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", encounterId.toString(),
                "type", "Encounter",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/close")
    @Transactional
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CloseEncounterRequest request) {

        Encounter encounter = encounterRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        encounter.close();
        encounterRepository.save(encounter);

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.closed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Encounter",
                id.toString(),
                Map.of(
                        "encounter_id", id.toString(),
                        "status", "COMPLETED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(encounter));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Encounter e) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", e.getFacilityId());
        attributes.put("patient_id", e.getPatientId());
        attributes.put("shift_id", e.getShiftId());
        attributes.put("encounter_type", e.getEncounterType());
        attributes.put("status", e.getStatus());
        attributes.put("chief_complaint", e.getChiefComplaint());
        attributes.put("diagnosis", e.getDiagnosis());
        attributes.put("notes", e.getNotes());
        attributes.put("vitals", e.getVitals());
        attributes.put("started_at", e.getStartedAt());
        attributes.put("ended_at", e.getEndedAt());
        attributes.put("created_at", e.getCreatedAt());
        attributes.put("updated_at", e.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", e.getId().toString());
        resource.put("type", "Encounter");
        resource.put("attributes", attributes);
        return resource;
    }
}
