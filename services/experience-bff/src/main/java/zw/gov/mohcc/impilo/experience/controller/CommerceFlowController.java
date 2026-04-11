package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;

/**
 * Canonical Experience commerce flow endpoints over MSIKA Flow.
 */
@RestController
@RequestMapping("/internal/v1/commerce")
public class CommerceFlowController {

    private static final Logger log = LoggerFactory.getLogger(CommerceFlowController.class);

    private final MsikaFlowServiceClient msikaFlowClient;

    public CommerceFlowController(MsikaFlowServiceClient msikaFlowClient) {
        this.msikaFlowClient = msikaFlowClient;
    }

    @PostMapping(value = "/cart/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> validateCart(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("cart validation", requestId, correlationId, () -> msikaFlowClient.validateCart(body));
    }

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createOrder(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order create", requestId, correlationId, () -> msikaFlowClient.createOrder(body));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<String> getOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order fetch", requestId, correlationId, () -> msikaFlowClient.getOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<String> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order cancel", requestId, correlationId, () -> msikaFlowClient.cancelOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/validate")
    public ResponseEntity<String> validateOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order validate", requestId, correlationId, () -> msikaFlowClient.validateOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/price")
    public ResponseEntity<String> priceOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order pricing", requestId, correlationId, () -> msikaFlowClient.priceOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<String> payOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order payment intent", requestId, correlationId, () -> msikaFlowClient.payOrder(orderId));
    }

    @GetMapping("/orders/{orderId}/tracking")
    public ResponseEntity<String> getTracking(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("order tracking", requestId, correlationId, () -> msikaFlowClient.getTracking(orderId));
    }

    @PostMapping("/orders/{orderId}/pickup/issue")
    public ResponseEntity<String> issuePickupToken(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("pickup issue", requestId, correlationId, () -> msikaFlowClient.issuePickupToken(orderId));
    }

    @PostMapping(value = "/pickup/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> claimPickup(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("pickup claim", requestId, correlationId, () -> msikaFlowClient.claimPickup(body));
    }

    private ResponseEntity<String> forward(
            String action,
            String requestId,
            String correlationId,
            UpstreamCall call) {
        try {
            ResponseEntity<String> upstream = call.run();
            return forward(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Commerce flow {} failed status={}", action, exception.getStatusCode());
            return forward(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> forward(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> forward(HttpStatusCodeException upstream, String requestId, String correlationId) {
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
