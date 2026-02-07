package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

/**
 * Audit event viewer — INTERNAL only.
 *
 * Exposes the event outbox as a read-only audit feed for operators.
 * Events are immutable once created.
 */
@RestController
@RequestMapping("/v1/internal/audit")
public class AuditController {

    private final EventOutboxRepository outboxRepository;

    public AuditController(EventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * GET /v1/internal/audit — paginated event feed.
     * Optional filter by aggregateType.
     */
    @GetMapping
    public ResponseEntity<?> listEvents(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        Page<EventOutboxEntity> events;
        if (aggregateType != null && !aggregateType.isBlank()) {
            events = outboxRepository.findByAggregateTypeOrderByCreatedAtDesc(
                    aggregateType, PageRequest.of(page, Math.min(size, 100)));
        } else {
            events = outboxRepository.findAllByOrderByCreatedAtDesc(
                    PageRequest.of(page, Math.min(size, 100)));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(events.getContent(), page, size, events.getTotalElements()),
                ctx.correlationId().toString()));
    }
}
