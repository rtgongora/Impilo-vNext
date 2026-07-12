package zw.gov.mohcc.impilo.msika.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Snapshot endpoints for MSIKA Products & Services Catalog (Ring 0).
 */
@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private final CatalogRepository catalogRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SnapshotController(CatalogRepository catalogRepository,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.catalogRepository = catalogRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/catalogs")
    public ResponseEntity<Map<String, Object>> catalogSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<CatalogEntity> page = catalogRepository.findByTenantIdOrTenantIdIsNull(
                UUID.fromString(tenantId),
                PageRequest.of(cursor, Math.min(limit, 500), Sort.by("catalogId")));

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

    /**
     * Emit a catalog-snapshot marker event through the real outbox. The
     * OutboxPublisher relays it to Kafka — no fabricated success: the response
     * mirrors exactly what was persisted for publication.
     */
    @PostMapping("/catalogs/emit")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<Map<String, Object>> emitCatalogSnapshot(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        long count = catalogRepository.count();
        OffsetDateTime emittedAt = OffsetDateTime.now();

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CATALOG");
        event.setAggregateId(tenantId);
        event.setEventType("CATALOG_SNAPSHOT_EMITTED");
        try {
            event.setTenantId(UUID.fromString(tenantId));
        } catch (IllegalArgumentException ignored) {
            // non-UUID tenant header: event still carries it in the payload
        }
        event.setCorrelationId(correlationId);
        event.setPodId(podId);
        event.setOccurredAt(emittedAt);
        try {
            event.setPayload(objectMapper.writeValueAsString(Map.of(
                    "entity", "Catalog",
                    "tenantId", tenantId,
                    "catalogCount", count,
                    "emittedAt", emittedAt.toString())));
        } catch (Exception e) {
            event.setPayload("{\"entity\":\"Catalog\",\"catalogCount\":" + count + "}");
        }
        event = outboxRepository.save(event);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SNAPSHOT_EMITTED");
        response.put("entity", "Catalog");
        response.put("tenant_id", tenantId);
        response.put("total_records", count);
        response.put("emitted_at", emittedAt.toString());
        response.put("outbox_event_id", event.getId());
        response.put("event_type", "CATALOG_SNAPSHOT_EMITTED");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(CatalogEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getCatalogId());
        item.put("type", "Catalog");
        item.put("name", entity.getName());
        item.put("version", entity.getVersion());
        item.put("status", entity.getStatus());
        item.put("scope", entity.getScope());
        return item;
    }
}
