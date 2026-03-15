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
import zw.gov.mohcc.impilo.support.domain.SupportMessageEntity;

import java.util.*;

@RestController
public class MessageController {

    private final SupportService supportService;

    public MessageController(SupportService supportService) { this.supportService = supportService; }

    @PostMapping("/internal/v1/support/tickets/{ticket_id}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable("ticket_id") UUID ticketId,
                                          @Valid @RequestBody SendMessageRequest request,
                                          jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            SupportMessageEntity message = supportService.sendMessage(ticketId, UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey,
                    request.senderRef(), request.senderType(), request.body());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(message));
        } catch (SupportService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/support/tickets/{ticket_id}/messages")
    public ResponseEntity<?> listMessages(@PathVariable("ticket_id") UUID ticketId,
                                           @RequestParam(defaultValue = "0") int cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        RequestContextHolder.require();
        Page<SupportMessageEntity> page = supportService.listMessages(ticketId, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private MessageResponse toResponse(SupportMessageEntity m) {
        return new MessageResponse(m.getMessageId(), m.getTicketId(), m.getSenderRef(), m.getSenderType(),
                m.getBody(), m.getCreatedAt());
    }
}
