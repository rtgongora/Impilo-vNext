package zw.gov.mohcc.impilo.support.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.support.api.dto.*;
import zw.gov.mohcc.impilo.support.core.SupportService;
import zw.gov.mohcc.impilo.support.domain.TicketEntity;

import java.util.*;

@RestController
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);
    private final SupportService supportService;

    public TicketController(SupportService supportService) { this.supportService = supportService; }

    @PostMapping("/internal/v1/support/tickets")
    public ResponseEntity<?> createTicket(@Valid @RequestBody CreateTicketRequest request,
                                           jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        TicketEntity ticket = supportService.createTicket(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), ctx.requestId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ticket));
    }

    @PatchMapping("/internal/v1/support/tickets/{ticket_id}")
    public ResponseEntity<?> updateTicket(@PathVariable("ticket_id") UUID ticketId,
                                           @RequestBody UpdateTicketRequest request,
                                           jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            TicketEntity ticket = supportService.updateTicket(ticketId, UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey, request);
            return ResponseEntity.ok(toResponse(ticket));
        } catch (SupportService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/support/tickets/{ticket_id}")
    public ResponseEntity<?> getTicket(@PathVariable("ticket_id") UUID ticketId) {
        RequestContext ctx = RequestContextHolder.require();
        return supportService.getTicket(ticketId)
                .map(t -> ResponseEntity.ok((Object) toResponse(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Ticket not found", ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/support/tickets")
    public ResponseEntity<?> listTickets(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String priority,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String assigneeRef,
                                          @RequestParam(defaultValue = "0") int cursor,
                                          @RequestParam(defaultValue = "20") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<TicketEntity> page = supportService.listTickets(UUID.fromString(ctx.tenantId()),
                status, priority, category, assigneeRef, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/internal/v1/support/tickets/{ticket_id}/escalate")
    public ResponseEntity<?> escalateTicket(@PathVariable("ticket_id") UUID ticketId,
                                             @Valid @RequestBody EscalateTicketRequest request,
                                             jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            TicketEntity ticket = supportService.escalateTicket(ticketId, UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey, request.escalatedBy(), request.targetLevel());
            return ResponseEntity.ok(toResponse(ticket));
        } catch (SupportService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    private TicketResponse toResponse(TicketEntity t) {
        return new TicketResponse(t.getTicketId(), t.getTenantId(), t.getTitle(), t.getDescription(),
                t.getCategory(), t.getPriority(), t.getStatus(), t.getReporterRef(), t.getAssigneeRef(),
                t.getFacilityRef(), t.getResolution(), t.getRequestId(), t.getCorrelationId(),
                t.getEscalationLevel(), t.getEscalatedAt(), t.getEscalatedBy(), t.getSlaBreachedAt(),
                t.getVersion(), t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt());
    }
}
