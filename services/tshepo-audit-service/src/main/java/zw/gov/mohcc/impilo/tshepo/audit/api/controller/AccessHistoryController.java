package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AccessHistoryEntry;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditQueryService;

import java.util.UUID;

/**
 * Patient-facing endpoint for "My Access History".
 *
 * Returns a redacted view of audit events — no internal IDs, resource references,
 * or detail payloads. Only exposes: event type, actor type, action, outcome,
 * purpose of use, and timestamp.
 *
 * This endpoint is called from the patient portal to show who accessed
 * the patient's data and why.
 */
@RestController
@RequestMapping("/v1/audit/access-history")
public class AccessHistoryController {

    private final AuditQueryService queryService;

    public AccessHistoryController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * GET /v1/audit/access-history/{subjectRef} — patient's access history.
     *
     * @param subjectRef the patient's subject reference (CPID)
     * @param tenantId   tenant isolation from trust header
     * @param page       page number (0-based)
     * @param size       page size (max 100)
     */
    @GetMapping("/{subjectRef}")
    public ResponseEntity<ApiResponse<PagedResponse<AccessHistoryEntry>>> getAccessHistory(
            @PathVariable String subjectRef,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AccessHistoryEntry> result = queryService.getAccessHistory(
                tenantId, subjectRef, page, Math.min(size, 100));

        PagedResponse<AccessHistoryEntry> pagedResponse = PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, corrId));
    }
}
