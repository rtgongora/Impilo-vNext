package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Patient;
import zw.gov.mohcc.impilo.experience.repository.PatientRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.*;

/**
 * GET  /internal/v1/patients — list patients with search, status, pagination.
 * GET  /internal/v1/patients/{id} — get single patient by ID.
 * POST /internal/v1/patients — register a new patient (provisional); publishes
 *      {@code impilo.experience.patient.registered.v1} to Kafka → VITO creates
 *      the authoritative identity and publishes back via {@code vito.identity}.
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public PatientController(PatientRepository patientRepository,
                             JdbcTemplate jdbcTemplate,
                             OutboxService outboxService) {
        this.patientRepository = patientRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatePatientRequest(
            @NotBlank String given_name,
            @NotBlank String family_name,
            String date_of_birth,
            String sex,
            String national_id,
            String phone,
            @NotBlank String facility_id
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePatientRequest req) {

        UUID id = UUID.randomUUID();
        String cpid = "CP-" + id.toString().substring(0, 8).toUpperCase();

        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, cpid, given_name, family_name, date_of_birth,
                    sex, national_id, phone, facility_id, status, created_at, updated_at)
                VALUES (?::uuid, ?, ?, ?, ?, ?::date, ?, ?, ?, ?, 'PROVISIONAL', NOW(), NOW())
                """,
                id.toString(), tenantId, cpid,
                req.given_name(), req.family_name(), req.date_of_birth(),
                req.sex(), req.national_id(), req.phone(), req.facility_id());

        outboxService.writeOutboxEvent(
                "impilo.experience.patient.registered.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Patient",
                id.toString(),
                Map.of(
                        "bff_patient_id", id.toString(),
                        "bff_cpid", cpid,
                        "tenant_id", tenantId,
                        "given_name", req.given_name(),
                        "family_name", req.family_name(),
                        "date_of_birth", req.date_of_birth() != null ? req.date_of_birth() : "",
                        "sex", req.sex() != null ? req.sex() : "",
                        "national_id", req.national_id() != null ? req.national_id() : "",
                        "phone", req.phone() != null ? req.phone() : "",
                        "facility_id", req.facility_id()
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "type", "Patient",
                "attributes", Map.of("cpid", cpid, "given_name", req.given_name(),
                        "family_name", req.family_name(), "status", "PROVISIONAL")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPatients(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("familyName").ascending());

        String pattern = (search == null || search.isBlank()) ? null : "%" + search.toLowerCase() + "%";
        Page<Patient> result = patientRepository.findByFilters(tenantId, pattern, status, pageable);

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
    public ResponseEntity<Map<String, Object>> getPatient(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Patient patient = patientRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(patient));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Patient p) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("cpid", p.getCpid());
        attributes.put("given_name", p.getGivenName());
        attributes.put("family_name", p.getFamilyName());
        attributes.put("date_of_birth", p.getDateOfBirth());
        attributes.put("sex", p.getSex());
        attributes.put("national_id", p.getNationalId());
        attributes.put("phone", p.getPhone());
        attributes.put("status", p.getStatus());
        attributes.put("facility_id", p.getFacilityId());
        attributes.put("created_at", p.getCreatedAt());
        attributes.put("updated_at", p.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", p.getId().toString());
        resource.put("type", "Patient");
        resource.put("attributes", attributes);
        return resource;
    }
}
