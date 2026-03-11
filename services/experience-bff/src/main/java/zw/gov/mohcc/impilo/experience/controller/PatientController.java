package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Patient;
import zw.gov.mohcc.impilo.experience.repository.PatientRepository;

import java.util.*;

/**
 * GET /internal/v1/patients — list patients with search, status, pagination.
 * GET /internal/v1/patients/{id} — get single patient by ID.
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private final PatientRepository patientRepository;

    public PatientController(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
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

        Page<Patient> result = patientRepository.findByFilters(tenantId, search, status, pageable);

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
