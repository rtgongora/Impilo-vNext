package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;

@RestController
@RequestMapping("/internal/v1/finance/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final MushexServiceClient mushexClient;

    public ReconciliationController(MushexServiceClient mushexClient) {
        this.mushexClient = mushexClient;
    }

    @PostMapping(value = "/import-statement", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> importStatement(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("import statement", requestId, correlationId, () -> mushexClient.importStatement(body));
    }

    @GetMapping("/unmatched")
    public ResponseEntity<String> getUnmatched(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list unmatched", requestId, correlationId,
                () -> mushexClient.getUnmatched(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> matchEntry(
            @RequestParam String reconId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("match entry", requestId, correlationId, () -> mushexClient.matchEntry(reconId, body));
    }

    private ResponseEntity<String> forward(String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Reconciliation {} failed status={}", action, exception.getStatusCode());
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
