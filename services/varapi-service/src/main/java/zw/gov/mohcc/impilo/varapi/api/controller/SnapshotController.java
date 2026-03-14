package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Snapshot endpoints for VARAPI Provider Registry (Ring 0).
 */
@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private final ProviderRepository providerRepository;

    public SnapshotController(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> providerSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<ProviderEntity> page = providerRepository.findAll(
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

    @PostMapping("/providers/emit")
    public ResponseEntity<Map<String, Object>> emitProviderSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        long count = providerRepository.count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SNAPSHOT_EMITTED");
        response.put("entity", "Provider");
        response.put("tenant_id", tenantId);
        response.put("total_records", count);
        response.put("emitted_at", OffsetDateTime.now().toString());
        response.put("topic", "impilo.varapi.snapshots");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(ProviderEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getProviderRef() != null ? entity.getProviderRef().toString() : null);
        item.put("type", "Provider");
        item.put("given_name", entity.getGivenName());
        item.put("family_name", entity.getFamilyName());
        item.put("profession", entity.getProfession());
        item.put("status", entity.getStatus());
        return item;
    }
}
