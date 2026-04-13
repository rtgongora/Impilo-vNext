package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.util.*;

/**
 * Audit log endpoints.
 * GET /internal/v1/admin/audit — list audit log entries, pagination, ordered by occurredAt DESC.
 * GET /internal/v1/admin/audit/{id} — get single audit entry.
 */
@RestController
@RequestMapping("/internal/v1/admin/audit")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final TshepoAuditServiceClient auditClient;

    public AuditController(TshepoAuditServiceClient auditClient) {
        this.auditClient = auditClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAuditEntries(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode data = auditClient.queryEvents(null, null, null, null, null, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        } catch (Exception e) {
            log.error("Audit list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAuditEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = auditClient.getEvent(id);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Audit get failed for id={}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
