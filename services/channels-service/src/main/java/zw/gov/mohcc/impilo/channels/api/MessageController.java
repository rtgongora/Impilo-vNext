package zw.gov.mohcc.impilo.channels.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.channels.api.dto.CreateMessageRequest;
import zw.gov.mohcc.impilo.channels.api.dto.MessageResponse;
import zw.gov.mohcc.impilo.channels.service.MessageService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/internal/v1/channels/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendOutbound(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateMessageRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        MessageResponse response = messageService.sendOutbound(request, ctx, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
