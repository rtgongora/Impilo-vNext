package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.AuditLogEntry;
import zw.gov.mohcc.impilo.experience.repository.AuditLogRepository;

import java.util.*;

/**
 * Audit log endpoints.
 * GET /internal/v1/admin/audit — list audit log entries, pagination, ordered by occurredAt DESC.
 * GET /internal/v1/admin/audit/{id} — get single audit entry.
 */
@RestController
@RequestMapping("/internal/v1/admin/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAuditEntries(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("occurredAt").descending());

        Page<AuditLogEntry> result = auditLogRepository.findByTenantId(tenantId, pageable);

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
    public ResponseEntity<Map<String, Object>> getAuditEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        AuditLogEntry entry = auditLogRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit entry not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(entry));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(AuditLogEntry a) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actor_id", a.getActorId());
        attributes.put("action", a.getAction());
        attributes.put("resource_type", a.getResourceType());
        attributes.put("resource_id", a.getResourceId());
        attributes.put("details", a.getDetails());
        attributes.put("ip_address", a.getIpAddress());
        attributes.put("occurred_at", a.getOccurredAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", a.getId().toString());
        resource.put("type", "AuditLogEntry");
        resource.put("attributes", attributes);
        return resource;
    }
}
