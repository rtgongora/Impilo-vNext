package zw.gov.mohcc.impilo.channels.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.channels.api.dto.AssistedInteractionResponse;
import zw.gov.mohcc.impilo.channels.api.dto.CreateAssistedInteractionRequest;
import zw.gov.mohcc.impilo.channels.service.AssistedInteractionService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/internal/v1/channels/assisted-interactions")
public class AssistedInteractionController {

    private final AssistedInteractionService assistedInteractionService;

    public AssistedInteractionController(AssistedInteractionService assistedInteractionService) {
        this.assistedInteractionService = assistedInteractionService;
    }

    @PostMapping
    public ResponseEntity<AssistedInteractionResponse> recordInteraction(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAssistedInteractionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        AssistedInteractionResponse response = assistedInteractionService.record(request, ctx, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
