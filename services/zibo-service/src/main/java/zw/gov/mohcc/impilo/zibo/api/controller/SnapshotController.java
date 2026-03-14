package zw.gov.mohcc.impilo.zibo.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Snapshot endpoints for ZIBO Terminology & Definitions Service (Ring 0).
 */
@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private final ArtifactRepository artifactRepository;

    public SnapshotController(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @GetMapping("/artifacts")
    public ResponseEntity<Map<String, Object>> artifactSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<ArtifactEntity> page = artifactRepository.findByTenantId(
                UUID.fromString(tenantId),
                PageRequest.of(cursor, Math.min(limit, 500), Sort.by("artifactId")));

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

    @PostMapping("/artifacts/emit")
    public ResponseEntity<Map<String, Object>> emitArtifactSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        long count = artifactRepository.countByTenantId(UUID.fromString(tenantId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SNAPSHOT_EMITTED");
        response.put("entity", "Artifact");
        response.put("tenant_id", tenantId);
        response.put("total_records", count);
        response.put("emitted_at", OffsetDateTime.now().toString());
        response.put("topic", "impilo.zibo.snapshots");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(ArtifactEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getArtifactId() != null ? entity.getArtifactId().toString() : null);
        item.put("type", "Artifact");
        item.put("fhir_type", entity.getFhirType() != null ? entity.getFhirType().name() : null);
        item.put("canonical_url", entity.getCanonicalUrl());
        item.put("version", entity.getVersion());
        item.put("name", entity.getName());
        item.put("status", entity.getStatus() != null ? entity.getStatus().name() : null);
        return item;
    }
}
