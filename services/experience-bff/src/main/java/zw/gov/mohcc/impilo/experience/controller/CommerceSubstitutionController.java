package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;

@RestController
@RequestMapping("/internal/v1/commerce")
public class CommerceSubstitutionController {

    private static final Logger log = LoggerFactory.getLogger(CommerceSubstitutionController.class);

    private final MsikaFlowServiceClient msikaFlowClient;

    public CommerceSubstitutionController(MsikaFlowServiceClient msikaFlowClient) {
        this.msikaFlowClient = msikaFlowClient;
    }

    @GetMapping("/substitutions")
    public ResponseEntity<String> listSubstitutions(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list substitutions", requestId, correlationId,
                () -> msikaFlowClient.listSubstitutions(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/rx/{orderId}/substitution/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> approveSubstitution(
            @PathVariable String orderId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("approve substitution", requestId, correlationId,
                () -> msikaFlowClient.approveSubstitution(orderId, normalizeBody(body)));
    }

    @PostMapping(value = "/rx/{orderId}/substitution/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> rejectSubstitution(
            @PathVariable String orderId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("reject substitution", requestId, correlationId,
                () -> msikaFlowClient.rejectSubstitution(orderId, normalizeBody(body)));
    }

    private static String normalizeBody(String body) {
        return body == null || body.isBlank() ? "{}" : body;
    }

    private ResponseEntity<String> forward(
            String action,
            String requestId,
            String correlationId,
            UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Commerce substitution {} failed status={}", action, exception.getStatusCode());
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
