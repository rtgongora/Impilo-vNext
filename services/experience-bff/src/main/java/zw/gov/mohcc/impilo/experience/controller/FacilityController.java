package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Facility;
import zw.gov.mohcc.impilo.experience.repository.FacilityRepository;

import java.util.*;

/**
 * Endpoint 1: Table-like read — GET /internal/v1/facilities
 * Queries actual Postgres with filtering and pagination.
 */
@RestController
@RequestMapping("/internal/v1/facilities")
public class FacilityController {

    private final FacilityRepository facilityRepository;

    public FacilityController(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @GetMapping
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

        String pattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%"
                : null;

        Page<Facility> result = facilityRepository.findByFilters(
                tenantId, status, facilityType, province, pattern, pageable);

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

    private Map<String, Object> toResource(Facility f) {
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
