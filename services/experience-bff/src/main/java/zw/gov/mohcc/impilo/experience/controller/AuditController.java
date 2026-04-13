package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.*;

/**
 * Audit log endpoints.
 * GET /internal/v1/admin/audit — list audit log entries, pagination, ordered by occurredAt DESC.
 * GET /internal/v1/admin/audit/{id} — get single audit entry.
 */
@RestController
@RequestMapping("/internal/v1/admin/audit")
public class AuditController {

    public AuditController() {
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAuditEntries(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
    throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
}

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAuditEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
    throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
}
}
