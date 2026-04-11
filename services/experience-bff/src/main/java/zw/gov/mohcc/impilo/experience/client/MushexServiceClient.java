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

@Component
public class MushexServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MushexServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MushexServiceClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.mushexBaseUrl();
    }

    public ResponseEntity<String> runSettlement(String requestBody) {
        log.info("MusheX: Running settlement");
        return postJson(baseUrl + "/mushex/v1/settlements/run", requestBody);
    }

    public ResponseEntity<String> getSettlement(String settlementId) {
        log.info("MusheX: Fetching settlement={}", settlementId);
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/settlements/" + settlementId, String.class);
    }

    public ResponseEntity<String> releasePayouts(String settlementId) {
        log.info("MusheX: Releasing payouts settlement={}", settlementId);
        return restTemplate.exchange(
                baseUrl + "/mushex/v1/settlements/" + settlementId + "/release-payouts",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class
        );
    }

    public ResponseEntity<String> importStatement(String requestBody) {
        log.info("MusheX: Importing reconciliation statement");
        return postJson(baseUrl + "/mushex/v1/recon/import-statement", requestBody);
    }

    public ResponseEntity<String> getUnmatched(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/recon/unmatched")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Fetching unmatched reconciliation entries");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> matchEntry(String reconId, String requestBody) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/recon/match")
                .queryParam("reconId", reconId)
                .toUriString();
        log.info("MusheX: Matching reconciliation entry reconId={}", reconId);
        return postJson(url, requestBody);
    }

    public ResponseEntity<String> getPaymentIntent(String intentId) {
        log.info("MusheX: Fetching payment intent={}", intentId);
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/payment-intents/" + intentId, String.class);
    }

    public ResponseEntity<String> createRefund(String intentId, String requestBody) {
        log.info("MusheX: Creating refund for intent={}", intentId);
        return postJson(baseUrl + "/mushex/v1/payment-intents/" + intentId + "/refund", requestBody);
    }

    public ResponseEntity<String> cancelIntent(String intentId) {
        log.info("MusheX: Cancelling payment intent={}", intentId);
        return restTemplate.exchange(
                baseUrl + "/mushex/v1/payment-intents/" + intentId + "/cancel",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class
        );
    }

    public ResponseEntity<String> getReceipts(String intentId) {
        log.info("MusheX: Fetching receipts for intent={}", intentId);
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/payment-intents/" + intentId + "/receipts", String.class);
    }

    public ResponseEntity<String> issueRemittanceSlip(String intentId) {
        log.info("MusheX: Issuing remittance slip for intent={}", intentId);
        return restTemplate.exchange(
                baseUrl + "/mushex/v1/payment-intents/" + intentId + "/issue-remittance-slip",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class
        );
    }

    public ResponseEntity<String> claimRemittance(String requestBody) {
        log.info("MusheX: Claiming remittance token");
        return postJson(baseUrl + "/mushex/v1/remittance/claim", requestBody);
    }

    public ResponseEntity<String> listAdapters() {
        log.info("MusheX: Listing adapters");
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/adapters", String.class);
    }

    public ResponseEntity<String> listFraudFlags(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/fraud-flags")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Listing fraud flags");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listOpsReviews(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/ops-reviews")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Listing ops reviews");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> approveOpsReview(String reviewId, String requestBody) {
        log.info("MusheX: Approving ops review={}", reviewId);
        return postJson(baseUrl + "/mushex/v1/ops-reviews/" + reviewId + "/approve", requestBody);
    }

    public ResponseEntity<String> rejectOpsReview(String reviewId, String requestBody) {
        log.info("MusheX: Rejecting ops review={}", reviewId);
        return postJson(baseUrl + "/mushex/v1/ops-reviews/" + reviewId + "/reject", requestBody);
    }

    public ResponseEntity<String> getLedger(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/ledger")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Listing ledger entries");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getClaim(String claimId) {
        log.info("MusheX: Fetching claim={}", claimId);
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/claims/" + claimId, String.class);
    }

    public ResponseEntity<String> submitClaim(String claimId) {
        log.info("MusheX: Submitting claim={}", claimId);
        return restTemplate.exchange(
                baseUrl + "/mushex/v1/claims/" + claimId + "/submit",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class
        );
    }

    public ResponseEntity<String> disputeClaim(String claimId, String requestBody) {
        log.info("MusheX: Disputing claim={}", claimId);
        return postJson(baseUrl + "/mushex/v1/claims/" + claimId + "/dispute", requestBody);
    }

    private ResponseEntity<String> postJson(String url, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
    }

    private MultiValueMap<String, String> copy(MultiValueMap<String, String> source) {
        return source == null ? new LinkedMultiValueMap<>() : new LinkedMultiValueMap<>(source);
    }
}
