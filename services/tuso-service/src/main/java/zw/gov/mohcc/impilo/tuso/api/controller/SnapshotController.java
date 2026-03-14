package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Snapshot endpoints for TUSO Facility Registry (Ring 0).
 */
@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private final FacilityRepository facilityRepository;

    public SnapshotController(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> facilitySnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<FacilityEntity> page = facilityRepository.findByTenantId(
                UUID.fromString(tenantId),
                PageRequest.of(cursor, Math.min(limit, 500), Sort.by("id")));

        List<Map<String, Object>> items = page.getContent().stream()
                .map(this::toSnapshotItem)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("as_of", asOf.toString());
        response.put("cursor", cursor);
        response.put("limit", limit);
        response.put("has_more", page.hasNext());
        response.put("total", page.getTotalElements());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/facilities/emit")
    public ResponseEntity<Map<String, Object>> emitFacilitySnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        long count = facilityRepository.countByTenantId(UUID.fromString(tenantId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SNAPSHOT_EMITTED");
        response.put("entity", "Facility");
        response.put("tenant_id", tenantId);
        response.put("total_records", count);
        response.put("emitted_at", OffsetDateTime.now().toString());
        response.put("topic", "impilo.tuso.snapshots");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(FacilityEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId());
        item.put("type", "Facility");
        item.put("facility_code", entity.getFacilityCode());
        item.put("name", entity.getName());
        item.put("facility_type", entity.getFacilityType());
        item.put("province", entity.getProvince());
        item.put("district", entity.getDistrict());
        item.put("status", entity.getStatus());
        return item;
    }
}
