package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Snapshot endpoints for VITO Client Registry (Ring 0).
 * Provides temporal, cursor-based snapshots of the client registry.
 */
@RestController
@RequestMapping("/internal/v1")
public class SnapshotController {

    private final ClientRepository clientRepository;

    public SnapshotController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping("/snapshots/clients")
    public ResponseEntity<Map<String, Object>> clientSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<ClientEntity> page = clientRepository.findByTenantId(
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

    @GetMapping("/clients/snapshot")
    public ResponseEntity<Map<String, Object>> clientsSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "100") int limit) {

        int effectiveLimit = Math.min(Math.max(limit, 1), 500);
        List<ClientEntity> entities = clientRepository.getSnapshot(
                UUID.fromString(tenantId),
                cursor,
                PageRequest.of(0, effectiveLimit + 1));

        boolean hasMore = entities.size() > effectiveLimit;
        List<ClientEntity> snapshotItems = hasMore ? entities.subList(0, effectiveLimit) : entities;
        UUID nextCursor = hasMore ? snapshotItems.get(snapshotItems.size() - 1).getHealthId() : null;

        List<Map<String, Object>> items = snapshotItems.stream()
                .map(this::toSnapshotItem)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("as_of", OffsetDateTime.now().toString());
        response.put("items", items);
        response.put("next_cursor", nextCursor != null ? nextCursor.toString() : null);
        response.put("has_more", hasMore);
        response.put("limit", effectiveLimit);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/snapshots/clients/emit")
    public ResponseEntity<Map<String, Object>> emitClientSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        long count = clientRepository.countByTenantId(UUID.fromString(tenantId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SNAPSHOT_EMITTED");
        response.put("entity", "Client");
        response.put("tenant_id", tenantId);
        response.put("total_records", count);
        response.put("emitted_at", OffsetDateTime.now().toString());
        response.put("topic", "impilo.vito.snapshots");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(ClientEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getHealthId() != null ? entity.getHealthId().toString() : null);
        item.put("type", "Client");
        item.put("given_name", entity.getGivenName());
        item.put("family_name", entity.getFamilyName());
        item.put("date_of_birth", entity.getDateOfBirth() != null ? entity.getDateOfBirth().toString() : null);
        item.put("status", entity.getStatus() != null ? entity.getStatus().name() : null);
        return item;
    }
}
