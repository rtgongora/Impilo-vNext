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
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;

@RestController
@RequestMapping("/internal/v1/commerce/vendor")
public class VendorOperationsController {

    private static final Logger log = LoggerFactory.getLogger(VendorOperationsController.class);

    private final MsikaFlowServiceClient msikaFlowClient;

    public VendorOperationsController(MsikaFlowServiceClient msikaFlowClient) {
        this.msikaFlowClient = msikaFlowClient;
    }

    /**
     * Authenticated vendor binding (M8): resolve the caller's vendor profile from the
     * JWT actor (X-Actor-ID = Health ID) via msika-flow {@code GET /v1/vendors/by-actor/{actorId}}.
     * Upstream 404 passes through and means "no vendor bound to this actor".
     */
    @GetMapping("/me")
    public ResponseEntity<String> myVendor(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId
    ) {
        return forward("vendor me", requestId, correlationId,
                () -> msikaFlowClient.getVendorByActor(actorId));
    }

    @GetMapping("/{vendorId}/orders")
    public ResponseEntity<String> getVendorOrders(
            @PathVariable String vendorId,
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor orders", requestId, correlationId,
                () -> msikaFlowClient.getVendorOrders(vendorId, new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping("/{vendorId}/orders/{orderId}/accept")
    public ResponseEntity<String> acceptVendorOrder(
            @PathVariable String vendorId,
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor accept", requestId, correlationId,
                () -> msikaFlowClient.acceptVendorOrder(vendorId, orderId));
    }

    @PostMapping("/orders/{orderId}/mark-ready")
    public ResponseEntity<String> markReady(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor mark-ready", requestId, correlationId,
                () -> msikaFlowClient.markOrderReady(orderId));
    }

    @PostMapping("/orders/{orderId}/mark-delivered")
    public ResponseEntity<String> markDelivered(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor mark-delivered", requestId, correlationId,
                () -> msikaFlowClient.markOrderDelivered(orderId));
    }

    @PostMapping("/orders/{orderId}/out-for-delivery")
    public ResponseEntity<String> outForDelivery(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor out-for-delivery", requestId, correlationId,
                () -> msikaFlowClient.markOrderOutForDelivery(orderId));
    }

    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<String> completeOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor complete", requestId, correlationId,
                () -> msikaFlowClient.completeOrder(orderId));
    }

    @PostMapping(value = "/rx/{orderId}/substitution/propose", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> proposeSubstitution(
            @PathVariable String orderId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("vendor substitution", requestId, correlationId,
                () -> msikaFlowClient.proposeSubstitution(orderId, body));
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
            log.warn("Vendor operations {} failed status={}", action, exception.getStatusCode());
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
