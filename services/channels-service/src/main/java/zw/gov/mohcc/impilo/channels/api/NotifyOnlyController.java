package zw.gov.mohcc.impilo.channels.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.channels.api.dto.NotifyOnlyRequest;
import zw.gov.mohcc.impilo.channels.api.dto.NotifyOnlyResult;
import zw.gov.mohcc.impilo.channels.service.NotifyOnlyService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

/**
 * External notify-only dispatch — a content-free prompt over an external channel telling the
 * recipient to log in to view something securely (e.g. an OROS diagnostic result). Carries no
 * clinical content by construction; honest configured/not-configured status.
 */
@RestController
@RequestMapping("/internal/v1/channels/notify-only")
public class NotifyOnlyController {

    private final NotifyOnlyService notifyOnlyService;

    public NotifyOnlyController(NotifyOnlyService notifyOnlyService) {
        this.notifyOnlyService = notifyOnlyService;
    }

    @PostMapping
    public ResponseEntity<NotifyOnlyResult> notifyOnly(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody NotifyOnlyRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        NotifyOnlyResult result = notifyOnlyService.send(
                ctx, request.channelType(), request.recipient(), request.reference(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
