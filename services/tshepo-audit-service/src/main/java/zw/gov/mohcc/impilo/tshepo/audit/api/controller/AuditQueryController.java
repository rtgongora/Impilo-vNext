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
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventQueryParams;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventResponse;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditQueryService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Query endpoint for authorized consumers to search and retrieve audit events.
 * All queries are tenant-isolated via the x-tenant-id header.
 */
@RestController
@RequestMapping("/v1/audit/events")
public class AuditQueryController {

    private final AuditQueryService queryService;

    public AuditQueryController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * GET /v1/audit/events — paginated, filtered query of audit events.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AuditEventResponse>>> queryEvents(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String subjectRef,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant fromTime,
            @RequestParam(required = false) Instant toTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditEventQueryParams params = new AuditEventQueryParams(
                tenantId, actorId, subjectRef, eventType, fromTime, toTime, page, size);

        Page<AuditEventResponse> result = queryService.queryEvents(params);

        PagedResponse<AuditEventResponse> pagedResponse = PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, corrId));
    }

    /**
     * GET /v1/audit/events/{id} — retrieve a single audit event by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEventResponse>> getEvent(
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId,
            @PathVariable UUID id) {

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();

        Optional<AuditEventResponse> result = queryService.findEventById(tenantId, id);

        return result.map(event ->
                ResponseEntity.ok(ApiResponse.ok(event, corrId))
        ).orElseGet(() ->
                ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND",
                                "Audit event not found: " + id, 404, corrId))
        );
    }
}
