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
import zw.gov.mohcc.impilo.experience.domain.Immunization;
import zw.gov.mohcc.impilo.experience.repository.ImmunizationRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Immunization management endpoints.
 * GET  /internal/v1/immunizations?patient_id= — list immunizations for patient (paged).
 * POST /internal/v1/immunizations — record immunization.
 */
@RestController
@RequestMapping("/internal/v1/immunizations")
public class ImmunizationsController {

    private final ImmunizationRepository immunizationRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public ImmunizationsController(ImmunizationRepository immunizationRepository,
                                   OutboxService outboxService,
                                   JdbcTemplate jdbcTemplate) {
        this.immunizationRepository = immunizationRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateImmunizationRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String vaccine_name,
            String vaccine_code,
            Integer dose_number,
            String dose_sequence,
            String lot_number,
            String site,
            String route,
            String administered_by,
            LocalDate expiration_date,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listImmunizations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<Immunization> result;
        if (patientId != null) {
            result = immunizationRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = immunizationRepository.findAll(pageable);
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
    public ResponseEntity<Map<String, Object>> createImmunization(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateImmunizationRequest request) {

        UUID immunizationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO immunizations
                (id, tenant_id, patient_id, encounter_id, vaccine_name,
                 vaccine_code, dose_number, dose_sequence, lot_number,
                 site, route, status, administered_by, administered_at,
                 expiration_date, notes, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?)
            """,
                immunizationId, tenantId, request.patient_id(), request.encounter_id(),
                request.vaccine_name(),
                request.vaccine_code(), request.dose_number(), request.dose_sequence(),
                request.lot_number(),
                request.site(), request.route(),
                request.administered_by(), now,
                request.expiration_date(), request.notes(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.immunization.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Immunization",
                immunizationId.toString(),
                Map.of(
                        "immunization_id", immunizationId.toString(),
                        "patient_id", request.patient_id(),
                        "vaccine_name", request.vaccine_name(),
                        "vaccine_code", request.vaccine_code() != null ? request.vaccine_code() : "",
                        "dose_number", request.dose_number() != null ? request.dose_number().toString() : "",
                        "status", "COMPLETED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("vaccine_name", request.vaccine_name());
        attributes.put("vaccine_code", request.vaccine_code());
        attributes.put("dose_number", request.dose_number());
        attributes.put("dose_sequence", request.dose_sequence());
        attributes.put("lot_number", request.lot_number());
        attributes.put("site", request.site());
        attributes.put("route", request.route());
        attributes.put("status", "COMPLETED");
        attributes.put("administered_by", request.administered_by());
        attributes.put("administered_at", now);
        attributes.put("expiration_date", request.expiration_date());
        attributes.put("notes", request.notes());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", immunizationId.toString(),
                "type", "Immunization",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Map<String, Object> toResource(Immunization i) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", i.getPatientId());
        attributes.put("encounter_id", i.getEncounterId());
        attributes.put("vaccine_name", i.getVaccineName());
        attributes.put("vaccine_code", i.getVaccineCode());
        attributes.put("dose_number", i.getDoseNumber());
        attributes.put("dose_sequence", i.getDoseSequence());
        attributes.put("lot_number", i.getLotNumber());
        attributes.put("site", i.getSite());
        attributes.put("route", i.getRoute());
        attributes.put("status", i.getStatus());
        attributes.put("administered_by", i.getAdministeredBy());
        attributes.put("administered_at", i.getAdministeredAt());
        attributes.put("expiration_date", i.getExpirationDate());
        attributes.put("notes", i.getNotes());
        attributes.put("created_at", i.getCreatedAt());
        attributes.put("updated_at", i.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", i.getId().toString());
        resource.put("type", "Immunization");
        resource.put("attributes", attributes);
        return resource;
    }
}
