package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.domain.Patient;
import zw.gov.mohcc.impilo.experience.repository.PatientRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.*;

/**
 * GET  /internal/v1/patients — list patients with search, status, pagination.
 * GET  /internal/v1/patients/{id} — get single patient by ID.
 * POST /internal/v1/patients — register a new patient.
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;
    private final VitoServiceClient vitoClient;

    public PatientController(PatientRepository patientRepository, JdbcTemplate jdbcTemplate,
                             VitoServiceClient vitoClient) {
        this.patientRepository = patientRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.vitoClient = vitoClient;
    }

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
    public ResponseEntity<Map<String, Object>> createPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreatePatientRequest req) {

        // STRANGLER: migrated — delegate patient registration to VITO
        Map<String, Object> patientData = new LinkedHashMap<>();
        patientData.put("given_name", req.given_name());
        patientData.put("family_name", req.family_name());
        patientData.put("date_of_birth", req.date_of_birth());
        patientData.put("sex", req.sex());
        patientData.put("national_id", req.national_id());
        patientData.put("phone", req.phone());
        patientData.put("facility_id", req.facility_id());
        patientData.put("tenant_id", tenantId);

        JsonNode result = vitoClient.registerPatient(patientData);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT
        // jdbcTemplate.update("""
        //         INSERT INTO patients (id, tenant_id, cpid, given_name, family_name, date_of_birth,
        //             sex, national_id, phone, facility_id, status, created_at, updated_at)
        //         VALUES (?::uuid, ?, ?, ?, ?, ?::date, ?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
        //         """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
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

        // STRANGLER: migrated — delegate to VITO
        JsonNode result = vitoClient.listPatients(search, status, page, Math.min(size, 100));

        // STRANGLER: migrated — was PatientRepository.findByFilters
        // PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("familyName").ascending());
        // Page<Patient> result = patientRepository.findByFilters(tenantId, search, status, pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPatient(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to VITO
        JsonNode result = vitoClient.getPatient(id.toString());

        // STRANGLER: migrated — was PatientRepository.findByIdAndTenantId
        // Patient patient = patientRepository.findByIdAndTenantId(id, tenantId)
        //         .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
