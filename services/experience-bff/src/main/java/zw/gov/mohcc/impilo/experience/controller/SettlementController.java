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
@RequestMapping("/internal/v1/finance/settlements")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final MushexServiceClient mushexClient;

    public SettlementController(MushexServiceClient mushexClient) {
        this.mushexClient = mushexClient;
    }

    @PostMapping(value = "/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> runSettlement(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("run settlement", requestId, correlationId, () -> mushexClient.runSettlement(body));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<String> getSettlement(
            @PathVariable String settlementId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("get settlement", requestId, correlationId, () -> mushexClient.getSettlement(settlementId));
    }

    @PostMapping("/{settlementId}/release-payouts")
    public ResponseEntity<String> releasePayouts(
            @PathVariable String settlementId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("release payouts", requestId, correlationId, () -> mushexClient.releasePayouts(settlementId));
    }

    private ResponseEntity<String> forward(
            String action,
            String requestId,
            String correlationId,
            UpstreamCall call
    ) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Settlement {} failed status={}", action, exception.getStatusCode());
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
        MediaType contentType = upstream.getResponseHeaders() != null
                ? upstream.getResponseHeaders().getContentType()
                : null;
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
