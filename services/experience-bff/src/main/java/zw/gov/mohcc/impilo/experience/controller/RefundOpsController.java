package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;

@RestController
@RequestMapping("/internal/v1/finance/refunds")
public class RefundOpsController {

    private static final Logger log = LoggerFactory.getLogger(RefundOpsController.class);

    private final MushexServiceClient mushexClient;

    public RefundOpsController(MushexServiceClient mushexClient) {
        this.mushexClient = mushexClient;
    }

    @GetMapping("/payment-intents/{intentId}")
    public ResponseEntity<String> getPaymentIntent(
            @PathVariable String intentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("get intent", requestId, correlationId, () -> mushexClient.getPaymentIntent(intentId));
    }

    @PostMapping(value = "/payment-intents/{intentId}/refund", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createRefund(
            @PathVariable String intentId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("create refund", requestId, correlationId, () -> mushexClient.createRefund(intentId, body));
    }

    private ResponseEntity<String> forward(String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Refund ops {} failed status={}", action, exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null ? upstream.getResponseHeaders().getContentType() : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }
}
