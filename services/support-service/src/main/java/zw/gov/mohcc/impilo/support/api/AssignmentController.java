package zw.gov.mohcc.impilo.support.api;

import jakarta.validation.Valid;
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
import zw.gov.mohcc.impilo.support.domain.AssignmentEntity;

import java.util.*;

@RestController
public class AssignmentController {

    private final SupportService supportService;

    public AssignmentController(SupportService supportService) { this.supportService = supportService; }

    @PostMapping("/internal/v1/support/tickets/{ticket_id}/assign")
    public ResponseEntity<?> assignTicket(@PathVariable("ticket_id") UUID ticketId,
                                           @Valid @RequestBody AssignTicketRequest request,
                                           jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            AssignmentEntity assignment = supportService.assignTicket(ticketId, UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey, request.assigneeRef(), request.assignedBy());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(assignment));
        } catch (SupportService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/support/tickets/{ticket_id}/assignments")
    public ResponseEntity<?> listAssignments(@PathVariable("ticket_id") UUID ticketId,
                                              @RequestParam(defaultValue = "0") int cursor,
                                              @RequestParam(defaultValue = "20") int limit) {
        RequestContextHolder.require();
        Page<AssignmentEntity> page = supportService.listAssignments(ticketId, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private AssignmentResponse toResponse(AssignmentEntity a) {
        return new AssignmentResponse(a.getAssignmentId(), a.getTicketId(), a.getAssigneeRef(),
                a.getAssignedBy(), a.getAssignedAt());
    }
}
