package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * OF-B7 — msika-flow regulated request-for-offer client
 * ({@code /v1/marketplace-requests}: patient comparison + selection).
 *
 * <p>Deliberately separate from {@link MsikaFlowServiceClient} (commerce
 * cart/order/logistics lanes): this client speaks only the OF-B4/B6/B7/B14/B17
 * marketplace-request surface, so the comparison lane can evolve without
 * touching the commerce client. Same base URL config
 * ({@code impilo.services.msika-flow-base-url} / {@code MSIKA_FLOW_BASE_URL},
 * default port 8100).</p>
 *
 * <p>All payloads are passed through verbatim (PII-free by upstream
 * construction — §11.8 published snapshots only). The selection idempotency
 * key is set explicitly per call so a caller-supplied key always reaches
 * msika-flow even when a BFF-local mutation fan-out has claimed the inbound
 * header for another leg.</p>
 */
@Component
public class MarketplaceFlowServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceFlowServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MarketplaceFlowServiceClient(RestTemplate serviceRestTemplate,
                                        ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.msikaFlowBaseUrl();
    }

    /** Patient/vendor view — §11.8-minimised published snapshot + windows/status. */
    public ResponseEntity<String> getRequest(String requestId) {
        log.info("Marketplace flow: fetching request={}", requestId);
        return restTemplate.getForEntity(
                baseUrl + "/v1/marketplace-requests/" + requestId, String.class);
    }

    /** Ranked comparison listing — OfferView[] with rankedBecause + financials. */
    public ResponseEntity<String> listOffers(String requestId) {
        log.info("Marketplace flow: listing offers for request={}", requestId);
        return restTemplate.getForEntity(
                baseUrl + "/v1/marketplace-requests/" + requestId + "/offers", String.class);
    }

    /** RC-1 idempotent single-offer commit ({@code {offerId}} body). */
    public ResponseEntity<String> select(String requestId, String requestBody, String idempotencyKey) {
        log.info("Marketplace flow: selecting offer on request={}", requestId);
        return postJson(baseUrl + "/v1/marketplace-requests/" + requestId + "/select",
                requestBody, idempotencyKey);
    }

    /** OF-B14 split commit ({@code {offerIds:[]}} body) — honest PARTIAL_COMMIT rollup. */
    public ResponseEntity<String> selectSplit(String requestId, String requestBody, String idempotencyKey) {
        log.info("Marketplace flow: split-selecting offers on request={}", requestId);
        return postJson(baseUrl + "/v1/marketplace-requests/" + requestId + "/select-split",
                requestBody, idempotencyKey);
    }

    /** OF-B17 fulfilment-pathway election (PICKUP | DELIVERY + delivery-minimum block). */
    public ResponseEntity<String> electPathway(String requestId, String requestBody) {
        log.info("Marketplace flow: electing fulfilment pathway on request={}", requestId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + "/v1/marketplace-requests/" + requestId + "/fulfilment-pathway",
                HttpMethod.PUT, new HttpEntity<>(requestBody, headers), String.class);
    }

    /** Patient-side cancel (optional {@code {reason}} body). */
    public ResponseEntity<String> cancel(String requestId, String requestBody) {
        log.info("Marketplace flow: cancelling request={}", requestId);
        return postJson(baseUrl + "/v1/marketplace-requests/" + requestId + "/cancel",
                requestBody != null ? requestBody : "{}", null);
    }

    private ResponseEntity<String> postJson(String url, String requestBody, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set(CompanionHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        }
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), String.class);
    }
}
