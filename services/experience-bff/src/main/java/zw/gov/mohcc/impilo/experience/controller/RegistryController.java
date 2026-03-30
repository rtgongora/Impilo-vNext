package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Facility;
import zw.gov.mohcc.impilo.experience.domain.RegistryProvider;
import zw.gov.mohcc.impilo.experience.repository.FacilityRepository;
import zw.gov.mohcc.impilo.experience.repository.RegistryProviderRepository;

import java.util.*;

/**
 * Registry endpoints.
 * GET /internal/v1/registry/providers — list providers with search filter, pagination.
 * GET /internal/v1/registry/providers/{id} — get single provider.
 * GET /internal/v1/registry/facilities — list facilities (duplicate access path for registry zone).
 */
@RestController
@RequestMapping("/internal/v1/registry")
public class RegistryController {

    private final RegistryProviderRepository registryProviderRepository;
    private final FacilityRepository facilityRepository;

    public RegistryController(RegistryProviderRepository registryProviderRepository,
                              FacilityRepository facilityRepository) {
        this.registryProviderRepository = registryProviderRepository;
        this.facilityRepository = facilityRepository;
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> listProviders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("familyName").ascending());

        String pattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%"
                : null;

        Page<RegistryProvider> result = registryProviderRepository.findByFilters(tenantId, pattern, pageable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toProviderResource)
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

    @GetMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> getProvider(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        RegistryProvider provider = registryProviderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toProviderResource(provider));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> listFacilities(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "facility_type") String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String search) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name").ascending());

        String facilityPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%"
                : null;

        Page<Facility> result = facilityRepository.findByFilters(
                tenantId, status, facilityType, province, facilityPattern, pageable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toFacilityResource)
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

    private Map<String, Object> toProviderResource(RegistryProvider r) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("provider_number", r.getProviderNumber());
        attributes.put("given_name", r.getGivenName());
        attributes.put("family_name", r.getFamilyName());
        attributes.put("qualification", r.getQualification());
        attributes.put("speciality", r.getSpeciality());
        attributes.put("facility_id", r.getFacilityId());
        attributes.put("status", r.getStatus());
        attributes.put("created_at", r.getCreatedAt());
        attributes.put("updated_at", r.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", r.getId().toString());
        resource.put("type", "RegistryProvider");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> toFacilityResource(Facility f) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", f.getName());
        attributes.put("code", f.getCode());
        attributes.put("facility_type", f.getFacilityType());
        attributes.put("status", f.getStatus());
        attributes.put("province", f.getProvince());
        attributes.put("district", f.getDistrict());
        attributes.put("latitude", f.getLatitude());
        attributes.put("longitude", f.getLongitude());
        attributes.put("capabilities", f.getCapabilities());
        attributes.put("created_at", f.getCreatedAt());
        attributes.put("updated_at", f.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", f.getId().toString());
        resource.put("type", "Facility");
        resource.put("attributes", attributes);
        return resource;
    }
}
