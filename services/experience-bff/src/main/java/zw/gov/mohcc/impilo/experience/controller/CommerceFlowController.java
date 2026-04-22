package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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

    // ── Persisted carts (msika-flow) ────────────────────────────────────────

    @GetMapping("/cart")
    public ResponseEntity<String> getOrCreateOpenCart(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("open cart", requestId, correlationId,
                () -> msikaFlowClient.getOrCreateOpenCart(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/cart/{cartId}/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addCartItem(
            @PathVariable String cartId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("add cart item", requestId, correlationId, () -> msikaFlowClient.addCartItem(cartId, body));
    }

    @PostMapping("/cart/{cartId}/items/remove")
    public ResponseEntity<String> removeCartItem(
            @PathVariable String cartId,
            @RequestParam String msikaCoreCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("remove cart item", requestId, correlationId, () -> msikaFlowClient.removeCartItem(cartId, msikaCoreCode));
    }

    @PostMapping(value = "/cart/{cartId}/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> checkoutCart(
            @PathVariable String cartId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("checkout cart", requestId, correlationId, () -> msikaFlowClient.checkoutCart(cartId, body));
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

    // ── Logistics orchestration (plans/exceptions/proofs) ───────────────────

    @PostMapping(value = "/logistics/plans", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createDeliveryPlan(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("create delivery plan", requestId, correlationId, () -> msikaFlowClient.createDeliveryPlan(body));
    }

    @GetMapping("/logistics/plans")
    public ResponseEntity<String> listDeliveryPlans(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list delivery plans", requestId, correlationId,
                () -> msikaFlowClient.listDeliveryPlans(new LinkedMultiValueMap<>(queryParams)));
    }

    @PatchMapping(value = "/logistics/plans/{planId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateDeliveryPlan(
            @PathVariable String planId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("update delivery plan", requestId, correlationId, () -> msikaFlowClient.updateDeliveryPlan(planId, body));
    }

    @PatchMapping(value = "/logistics/legs/{legId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateDeliveryLeg(
            @PathVariable String legId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("update delivery leg", requestId, correlationId, () -> msikaFlowClient.updateDeliveryLeg(legId, body));
    }

    @PostMapping(value = "/logistics/exceptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> raiseDeliveryException(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("raise exception", requestId, correlationId, () -> msikaFlowClient.raiseDeliveryException(body));
    }

    @GetMapping("/logistics/exceptions")
    public ResponseEntity<String> listDeliveryExceptions(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list exceptions", requestId, correlationId,
                () -> msikaFlowClient.listDeliveryExceptions(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/logistics/proofs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordProof(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("record proof", requestId, correlationId, () -> msikaFlowClient.recordProof(body));
    }

    @GetMapping("/logistics/proofs")
    public ResponseEntity<String> listProofs(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list proofs", requestId, correlationId,
                () -> msikaFlowClient.listProofs(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/logistics/providers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createLogisticsProvider(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("create logistics provider", requestId, correlationId, () -> msikaFlowClient.createLogisticsProvider(body));
    }

    @GetMapping("/logistics/providers")
    public ResponseEntity<String> listLogisticsProviders(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list logistics providers", requestId, correlationId,
                () -> msikaFlowClient.listLogisticsProviders(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/logistics/assets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createDeliveryAsset(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("create delivery asset", requestId, correlationId, () -> msikaFlowClient.createDeliveryAsset(body));
    }

    @GetMapping("/logistics/assets")
    public ResponseEntity<String> listDeliveryAssets(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list delivery assets", requestId, correlationId,
                () -> msikaFlowClient.listDeliveryAssets(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/logistics/missions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createDeliveryMission(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("create delivery mission", requestId, correlationId, () -> msikaFlowClient.createDeliveryMission(body));
    }

    @GetMapping("/logistics/missions")
    public ResponseEntity<String> listDeliveryMissions(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list delivery missions", requestId, correlationId,
                () -> msikaFlowClient.listDeliveryMissions(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/logistics/handoffs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordHandoff(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("record handoff", requestId, correlationId, () -> msikaFlowClient.recordHandoff(body));
    }

    @GetMapping("/logistics/handoffs")
    public ResponseEntity<String> listHandoffs(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list handoffs", requestId, correlationId,
                () -> msikaFlowClient.listHandoffs(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/logistics/custody", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createCustody(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("create custody record", requestId, correlationId, () -> msikaFlowClient.createCustody(body));
    }

    @GetMapping("/logistics/custody")
    public ResponseEntity<String> listCustody(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("list custody records", requestId, correlationId,
                () -> msikaFlowClient.listCustody(new LinkedMultiValueMap<>(queryParams)));
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
