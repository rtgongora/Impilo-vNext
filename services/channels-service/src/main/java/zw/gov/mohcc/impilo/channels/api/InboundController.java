package zw.gov.mohcc.impilo.channels.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.channels.api.dto.InboundMessageRequest;
import zw.gov.mohcc.impilo.channels.api.dto.MessageResponse;
import zw.gov.mohcc.impilo.channels.service.MessageService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/external/v1/channels/inbound")
public class InboundController {

    private final MessageService messageService;

    public InboundController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> receiveInbound(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InboundMessageRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        String key = idempotencyKey != null ? idempotencyKey : java.util.UUID.randomUUID().toString();
        MessageResponse response = messageService.receiveInbound(request, ctx, key);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
