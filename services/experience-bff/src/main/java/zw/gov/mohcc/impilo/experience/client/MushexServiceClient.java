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
import java.util.List;

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

    public ResponseEntity<String> listSettlements(String intentId, List<String> intentIds) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/settlements");
        if (intentId != null && !intentId.isBlank()) {
            builder.queryParam("intent_id", intentId);
        }
        if (intentIds != null) {
            for (String id : intentIds) {
                if (id != null && !id.isBlank()) {
                    builder.queryParam("intent_ids", id);
                }
            }
        }
        return restTemplate.getForEntity(builder.toUriString(), String.class);
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

    /**
     * Phase 3 — proxy {@code POST /mushex/v1/payment-intents/{intentId}/attempts}.
     * Forwards the JSON body verbatim and optionally the {@code X-Idempotency-Key}
     * header so attempts can be safely retried by clients.
     * See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md}.
     */
    public ResponseEntity<String> initiatePaymentAttempt(String intentId, String requestBody, String idempotencyKey) {
        log.info("MusheX: Initiating payment attempt for intent={} idempotency={}",
                intentId, idempotencyKey != null && !idempotencyKey.isBlank());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        String body = requestBody == null || requestBody.isBlank() ? "{}" : requestBody;
        return restTemplate.exchange(
                baseUrl + "/mushex/v1/payment-intents/" + intentId + "/attempts",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    public ResponseEntity<String> getReceipts(String intentId) {
        log.info("MusheX: Fetching receipts for intent={}", intentId);
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/payment-intents/" + intentId + "/receipts", String.class);
    }

    /**
     * Phase 3 follow-on — proxy
     * {@code POST /mushex/v1/payment-intents/{intentId}/attempts/reselect}.
     * No idempotency key: reselection is intentionally not money-movement.
     */
    public ResponseEntity<String> reselectRail(String intentId, String requestBody) {
        log.info("MusheX: Reselecting rail for intent={}", intentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = requestBody == null || requestBody.isBlank() ? "{}" : requestBody;
        return restTemplate.exchange(
                baseUrl + "/mushex/v1/payment-intents/" + intentId + "/attempts/reselect",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    /**
     * Phase 3 follow-on — proxy
     * {@code GET /mushex/v1/payment-intents/{intentId}/attempts} for read-only history.
     */
    public ResponseEntity<String> listPaymentAttempts(String intentId) {
        log.info("MusheX: Listing attempts for intent={}", intentId);
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/payment-intents/" + intentId + "/attempts",
                String.class
        );
    }

    /**
     * Phase 3 follow-on — proxy {@code GET /mushex/v1/payment-intents/{intentId}} so the
     * BFF can return the intent envelope (including {@code metadata.rail_selection}) for
     * the {@code /finance/payer-ops/intent/[intentId]} surface.
     */
    public ResponseEntity<String> getPaymentIntent(String intentId) {
        log.info("MusheX: Fetching intent={}", intentId);
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/payment-intents/" + intentId,
                String.class
        );
    }

    public ResponseEntity<String> listPaymentIntentsBySource(String sourceType, String sourceId, List<String> sourceIds) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/payment-intents")
                .queryParam("source_type", sourceType);
        if (sourceId != null && !sourceId.isBlank()) {
            builder.queryParam("source_id", sourceId);
        }
        if (sourceIds != null) {
            for (String id : sourceIds) {
                if (id != null && !id.isBlank()) {
                    builder.queryParam("source_ids", id);
                }
            }
        }
        String url = builder.toUriString();
        log.info("MusheX: Listing intents by source type={} sourceId={} sourceIds={}",
                sourceType, sourceId, sourceIds != null ? sourceIds.size() : 0);
        return restTemplate.getForEntity(url, String.class);
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
        // mushex-service exposes the adapter inventory via the platform adapter-readiness
        // endpoint; there is no bare GET /mushex/v1/adapters (that path is webhook-POST only).
        log.info("MusheX: Listing adapters (adapter-readiness)");
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/platform/adapter-readiness", String.class);
    }

    public ResponseEntity<String> listFraudFlags(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/fraud/flags")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Listing fraud flags");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listOpsReviews(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/ops/reviews/pending")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Listing ops reviews (pending)");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> approveOpsReview(String reviewId, String requestBody) {
        log.info("MusheX: Approving ops review={}", reviewId);
        return postJson(baseUrl + "/mushex/v1/ops/reviews/" + reviewId + "/approve", requestBody);
    }

    public ResponseEntity<String> rejectOpsReview(String reviewId, String requestBody) {
        log.info("MusheX: Rejecting ops review={}", reviewId);
        return postJson(baseUrl + "/mushex/v1/ops/reviews/" + reviewId + "/reject", requestBody);
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

    public ResponseEntity<String> listClaims(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/claims")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("MusheX: Listing claims");
        return restTemplate.getForEntity(url, String.class);
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

    public ResponseEntity<String> platformListWallets(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/platform/wallets")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> platformCreateWallet(String requestBody) {
        return postJson(baseUrl + "/mushex/v1/platform/wallets", requestBody);
    }

    public ResponseEntity<String> platformWalletCredit(String walletId, String requestBody) {
        return postJson(baseUrl + "/mushex/v1/platform/wallets/" + walletId + "/credit", requestBody);
    }

    public ResponseEntity<String> platformWalletDebit(String walletId, String requestBody) {
        return postJson(baseUrl + "/mushex/v1/platform/wallets/" + walletId + "/debit", requestBody);
    }

    public ResponseEntity<String> platformWalletTransactions(String walletId) {
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/platform/wallets/" + walletId + "/transactions", String.class);
    }

    public ResponseEntity<String> platformWalletById(String walletId) {
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/platform/wallets/" + walletId, String.class);
    }

    public ResponseEntity<String> platformListRemittanceTransfers(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/remittance-transfers")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> platformCreateRemittanceTransfer(String requestBody) {
        return postJson(baseUrl + "/mushex/v1/remittance-transfers", requestBody);
    }

    public ResponseEntity<String> platformRemittanceTransferById(String transferId) {
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/remittance-transfers/" + transferId, String.class);
    }

    public ResponseEntity<String> platformListCardProfiles(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/mushex/v1/platform/card-profiles")
                .queryParams(copy(queryParams))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> platformCreateCardProfile(String requestBody) {
        return postJson(baseUrl + "/mushex/v1/platform/card-profiles", requestBody);
    }

    public ResponseEntity<String> platformCardProfileById(String cardProfileId) {
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/platform/card-profiles/" + cardProfileId, String.class);
    }

    public ResponseEntity<String> platformListReversals() {
        return restTemplate.getForEntity(baseUrl + "/mushex/v1/platform/reversals", String.class);
    }

    public ResponseEntity<String> platformReversalById(String reversalId) {
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/platform/reversals/" + reversalId, String.class);
    }

    public ResponseEntity<String> platformCreateReversal(String requestBody) {
        return postJson(baseUrl + "/mushex/v1/platform/reversals", requestBody);
    }

    /**
     * Read-only proxy for {@code GET /mushex/v1/platform/adapter-readiness}.
     * See Phase 2 of the MusheX/COSTA roadmap and
     * {@code zw.gov.mohcc.impilo.mushex.service.rail.AdapterReadinessService}.
     */
    public ResponseEntity<String> platformAdapterReadiness() {
        return restTemplate.getForEntity(
                baseUrl + "/mushex/v1/platform/adapter-readiness", String.class);
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
