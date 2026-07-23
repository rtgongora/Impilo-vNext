package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * Transactional MSIKA Flow client for cart validation and order progression.
 */
@Component
public class MsikaFlowServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MsikaFlowServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MsikaFlowServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.msikaFlowBaseUrl();
    }

    public ResponseEntity<String> validateCart(String requestBody) {
        log.info("MSIKA Flow: Validating commerce cart");
        return postJson(baseUrl + "/v1/cart/validate", requestBody);
    }

    public ResponseEntity<String> createOrder(String requestBody) {
        log.info("MSIKA Flow: Creating commerce order");
        return postJson(baseUrl + "/v1/orders", requestBody);
    }

    public ResponseEntity<String> getOrder(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId;
        log.info("MSIKA Flow: Fetching order={}", orderId);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listOrders(int page, int size, String facilityId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/orders")
                .queryParam("page", page)
                .queryParam("size", size);
        if (facilityId != null && !facilityId.isBlank()) {
            builder.queryParam("facility_id", facilityId);
        }
        String url = builder.toUriString();
        log.info("MSIKA Flow: Listing orders page={} size={} facility={}", page, size, facilityId);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listBookings(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/bookings")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.info("MSIKA Flow: Listing bookings page={} size={}", page, size);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> cancelOrder(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/cancel", "Cancelling");
    }

    public ResponseEntity<String> validateOrder(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/validate", "Validating");
    }

    public ResponseEntity<String> priceOrder(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/price", "Pricing");
    }

    public ResponseEntity<String> payOrder(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/pay", "Creating payment intent for");
    }

    public ResponseEntity<String> getTracking(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId + "/tracking";
        log.info("MSIKA Flow: Fetching tracking for order={}", orderId);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> issuePickupToken(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/pickup/issue", "Issuing pickup token for");
    }

    public ResponseEntity<String> claimPickup(String requestBody) {
        log.info("MSIKA Flow: Claiming pickup");
        return postJson(baseUrl + "/v1/pickup/claim", requestBody);
    }

    // ── Persisted carts ─────────────────────────────────────────────────────

    public ResponseEntity<String> getOrCreateOpenCart(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/carts/open")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> addCartItem(String cartId, String requestBody) {
        return postJson(baseUrl + "/v1/carts/" + cartId + "/items", requestBody);
    }

    public ResponseEntity<String> removeCartItem(String cartId, String msikaCoreCode) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/carts/" + cartId + "/items")
                .queryParam("msikaCoreCode", msikaCoreCode)
                .toUriString();
        return restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    }

    public ResponseEntity<String> checkoutCart(String cartId, String requestBody) {
        return postJson(baseUrl + "/v1/carts/" + cartId + "/checkout", requestBody);
    }

    // ── Logistics orchestration (plans/exceptions/proofs) ───────────────────

    public ResponseEntity<String> createDeliveryPlan(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/plans", requestBody);
    }

    public ResponseEntity<String> listDeliveryPlans(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/plans")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getDeliveryPlan(String planId) {
        return restTemplate.getForEntity(baseUrl + "/v1/logistics/plans/" + planId, String.class);
    }

    public ResponseEntity<String> updateDeliveryPlan(String planId, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + "/v1/logistics/plans/" + planId,
                HttpMethod.PATCH,
                new HttpEntity<>(requestBody, headers),
                String.class
        );
    }

    public ResponseEntity<String> updateDeliveryLeg(String legId, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + "/v1/logistics/legs/" + legId,
                HttpMethod.PATCH,
                new HttpEntity<>(requestBody, headers),
                String.class
        );
    }

    public ResponseEntity<String> raiseDeliveryException(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/exceptions", requestBody);
    }

    public ResponseEntity<String> listDeliveryExceptions(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/exceptions")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> recordProof(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/proofs", requestBody);
    }

    public ResponseEntity<String> listProofs(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/proofs")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> createLogisticsProvider(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/providers", requestBody);
    }

    public ResponseEntity<String> listLogisticsProviders(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/providers")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> createDeliveryAsset(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/assets", requestBody);
    }

    public ResponseEntity<String> listDeliveryAssets(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/assets")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> createDeliveryMission(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/missions", requestBody);
    }

    public ResponseEntity<String> listDeliveryMissions(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/missions")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> recordHandoff(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/handoffs", requestBody);
    }

    public ResponseEntity<String> listHandoffs(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/handoffs")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> createCustody(String requestBody) {
        return postJson(baseUrl + "/v1/logistics/custody", requestBody);
    }

    public ResponseEntity<String> listCustody(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/logistics/custody")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getVendorOrders(String vendorId, MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/vendors/" + vendorId + "/orders")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MSIKA Flow: Fetching vendor orders vendorId={}", vendorId);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> acceptVendorOrder(String vendorId, String orderId) {
        return postWithoutBody(baseUrl + "/v1/vendors/" + vendorId + "/orders/" + orderId + "/accept", "Accepting vendor");
    }

    public ResponseEntity<String> markOrderReady(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/mark-ready", "Marking ready");
    }

    public ResponseEntity<String> markOrderDelivered(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/mark-delivered", "Marking delivered");
    }

    public ResponseEntity<String> markOrderOutForDelivery(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/out-for-delivery", "Marking out-for-delivery");
    }

    public ResponseEntity<String> completeOrder(String orderId) {
        return postWithoutBody(baseUrl + "/v1/orders/" + orderId + "/complete", "Completing");
    }

    public ResponseEntity<String> proposeSubstitution(String orderId, String requestBody) {
        log.info("MSIKA Flow: Proposing substitution for order={}", orderId);
        return postJson(baseUrl + "/v1/rx/" + orderId + "/substitution/propose", requestBody);
    }

    public ResponseEntity<String> listSubstitutions(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/rx/substitutions")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MSIKA Flow: Listing substitutions");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> approveSubstitution(String orderId, String requestBody) {
        log.info("MSIKA Flow: Approving substitution for order={}", orderId);
        return postJson(baseUrl + "/v1/rx/" + orderId + "/substitution/approve", requestBody);
    }

    public ResponseEntity<String> rejectSubstitution(String orderId, String requestBody) {
        log.info("MSIKA Flow: Rejecting substitution for order={}", orderId);
        return postJson(baseUrl + "/v1/rx/" + orderId + "/substitution/reject", requestBody);
    }

    public ResponseEntity<String> listOpsReviews(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ops/reviews/pending")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MSIKA Flow: Listing ops reviews");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> approveOpsReview(String reviewId, String requestBody) {
        log.info("MSIKA Flow: Approving ops review={}", reviewId);
        return postJson(baseUrl + "/v1/ops/reviews/" + reviewId + "/approve", requestBody);
    }

    public ResponseEntity<String> rejectOpsReview(String reviewId, String requestBody) {
        log.info("MSIKA Flow: Rejecting ops review={}", reviewId);
        return postJson(baseUrl + "/v1/ops/reviews/" + reviewId + "/reject", requestBody);
    }

    public ResponseEntity<String> getStuckOrders() {
        log.info("MSIKA Flow: Listing stuck orders");
        return restTemplate.getForEntity(baseUrl + "/v1/ops/stuck-orders", String.class);
    }

    public ResponseEntity<String> listAuditEvents(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ops/audit")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MSIKA Flow: Listing ops audit events");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listVendors(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/vendors")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MSIKA Flow: Listing vendors");
        return restTemplate.getForEntity(url, String.class);
    }

    /** Public approved-vendors directory (anonymous-safe) — ACTIVE vendors, allow-listed. */
    public com.fasterxml.jackson.databind.JsonNode publicVendors(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/public/vendors")
                .queryParams(copy(queryParams)).toUriString();
        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> r =
                restTemplate.getForEntity(url, com.fasterxml.jackson.databind.JsonNode.class);
        return r.getBody() == null ? null : r.getBody().get("data");
    }

    public ResponseEntity<String> getVendor(String vendorId) {
        String url = baseUrl + "/v1/vendors/" + vendorId;
        log.info("MSIKA Flow: Fetching vendor={}", vendorId);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getVendorByActor(String actorId) {
        String url = baseUrl + "/v1/vendors/by-actor/" + actorId;
        log.info("MSIKA Flow: Resolving vendor by actor");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> bindVendorActor(String vendorId, String requestBody) {
        log.info("MSIKA Flow: Binding actor to vendor={}", vendorId);
        return postJson(baseUrl + "/v1/vendors/" + vendorId + "/bind-actor", requestBody);
    }

    public ResponseEntity<String> suspendVendor(String vendorId, String requestBody) {
        log.info("MSIKA Flow: Suspending vendor={}", vendorId);
        return postJson(baseUrl + "/v1/ops/vendors/" + vendorId + "/suspend", requestBody);
    }

    public ResponseEntity<String> reinstateVendor(String vendorId) {
        log.info("MSIKA Flow: Reinstating vendor={}", vendorId);
        return restTemplate.exchange(
                baseUrl + "/v1/ops/vendors/" + vendorId + "/reinstate",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class
        );
    }

    private ResponseEntity<String> postJson(String url, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
    }

    private ResponseEntity<String> postWithoutBody(String url, String action) {
        log.info("MSIKA Flow: {} order at {}", action, url);
        return restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }

    private MultiValueMap<String, String> copy(MultiValueMap<String, String> source) {
        return source == null ? new LinkedMultiValueMap<>() : new LinkedMultiValueMap<>(source);
    }
}
