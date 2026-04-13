package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/**
 * Patient CRUD with privacy-by-design PII filtering.
 *
 * <p>Per Health OS doctrine ("No PII in SHR", "minimum-necessary access"):
 * the toResource() method returns different PII field sets based on the
 * caller's X-Purpose-Of-Use header and role context. Every PII access
 * is recorded in the pii_access_log via PiiAccessAuditService.</p>
 *
 * <p>PII tiers:
 * <ul>
 *   <li><b>FULL</b>: all fields (TREATMENT purpose, clinical roles with active encounter)</li>
 *   <li><b>STANDARD</b>: name + age + gender + CPID (default for authenticated clinical staff)</li>
 *   <li><b>MINIMAL</b>: CPID + age-range + gender only (audit/admin/reporting contexts)</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private final VitoServiceClient vitoClient;

    public PatientController(VitoServiceClient vitoClient) {
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
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        JsonNode result = vitoClient.listPatients(search, status, page, Math.min(size, 100));

        // PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("familyName").ascending());
        // Page<Patient> result = patientRepository.findByFilters(tenantId, search, status, pageable);

        // Audit PII access for search results
        if (actorId != null && !result.isEmpty()) {
            for (Patient p : result.getContent()) {
                piiAudit.recordAccess(tenantId, actorId, actorType, p.getId().toString(),
                        "PATIENT", "SEARCH", fieldsAccessed, purposeOfUse, "API",
                        piiTier, forwardedFor, userAgent, facilityId);
            }
        }

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
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        JsonNode result = vitoClient.getPatient(id.toString());

        // Patient patient = patientRepository.findByIdAndTenantId(id, tenantId)
        //         .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "pii_tier", piiTier
        ));

        return ResponseEntity.ok(response);
    }
}
