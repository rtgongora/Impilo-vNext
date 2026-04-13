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
@RequestMapping("/internal/v1/commerce/ops")
public class MarketplaceOpsController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceOpsController.class);

    private final MsikaFlowServiceClient msikaFlowClient;

    public MarketplaceOpsController(MsikaFlowServiceClient msikaFlowClient) {
        this.msikaFlowClient = msikaFlowClient;
    }

    @GetMapping("/reviews")
    public ResponseEntity<String> listReviews(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list reviews", requestId, correlationId,
                () -> msikaFlowClient.listOpsReviews(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/reviews/{reviewId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> approveReview(
            @PathVariable String reviewId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("approve review", requestId, correlationId,
                () -> msikaFlowClient.approveOpsReview(reviewId, normalizeBody(body)));
    }

    @PostMapping(value = "/reviews/{reviewId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> rejectReview(
            @PathVariable String reviewId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("reject review", requestId, correlationId,
                () -> msikaFlowClient.rejectOpsReview(reviewId, normalizeBody(body)));
    }

    @GetMapping("/stuck-orders")
    public ResponseEntity<String> getStuckOrders(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list stuck orders", requestId, correlationId, msikaFlowClient::getStuckOrders);
    }

    @GetMapping("/audit")
    public ResponseEntity<String> listAuditEvents(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @GetMapping("/vendors")
    public ResponseEntity<String> listVendors(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list vendors", requestId, correlationId,
                () -> msikaFlowClient.listVendors(new LinkedMultiValueMap<>(queryParams)));
    }

    @GetMapping("/vendors/{vendorId}")
    public ResponseEntity<String> getVendor(
            @PathVariable String vendorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("get vendor", requestId, correlationId, () -> msikaFlowClient.getVendor(vendorId));
    }

    @PostMapping(value = "/vendors/{vendorId}/suspend", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> suspendVendor(
            @PathVariable String vendorId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("suspend vendor", requestId, correlationId,
                () -> msikaFlowClient.suspendVendor(vendorId, normalizeBody(body)));
    }

    @PostMapping("/vendors/{vendorId}/reinstate")
    public ResponseEntity<String> reinstateVendor(
            @PathVariable String vendorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("reinstate vendor", requestId, correlationId, () -> msikaFlowClient.reinstateVendor(vendorId));
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
            log.warn("Marketplace ops {} failed status={}", action, exception.getStatusCode());
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
