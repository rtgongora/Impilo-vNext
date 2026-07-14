package zw.gov.mohcc.impilo.daidzai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Daidzai → mushe seam for assistance-case money. Mushe owns ALL money truth (escrow
 * wallet, contributions, refunds); daidzai only holds the returned ids. Campaign
 * creation FAILS CLOSED when mushe is unreachable — an assistance case without a money
 * backbone would be a dead surface, not a degraded one. Header propagation mirrors
 * {@link NdilaCatchmentClient}.
 */
@Component
public class MusheContributionClient {

    private static final Logger log = LoggerFactory.getLogger(MusheContributionClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public MusheContributionClient(
            @Value("${daidzai.mushe.base-url:http://mushe-wallet-service:8102}") String baseUrl,
            @Value("${daidzai.mushe.timeout-ms:5000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record CampaignMoneyLink(UUID contributionRequestId, String shareToken) {}

    /**
     * Mint the campaign-backed contribution request (origin=CAMPAIGN → mushe creates the
     * escrow wallet). Throws on failure — callers treat this as fail-closed.
     */
    public CampaignMoneyLink createCampaignRequest(UUID tenantId, UUID campaignRef,
                                                   UUID beneficiaryWalletId, String title,
                                                   BigDecimal targetAmount, String currency,
                                                   String createdBy) {
        Map<String, Object> body = new HashMap<>();
        body.put("origin", "CAMPAIGN");
        body.put("campaignRef", campaignRef.toString());
        body.put("beneficiaryTargetWalletId", beneficiaryWalletId.toString());
        body.put("title", title);
        body.put("targetAmount", targetAmount);
        if (currency != null && !currency.isBlank()) {
            body.put("currency", currency);
        }
        if (createdBy != null && !createdBy.isBlank()) {
            body.put("createdBy", createdBy);
        }
        JsonNode created = post("/internal/v1/bill-contributions", body, tenantId);
        if (created == null || !created.hasNonNull("id") || !created.hasNonNull("shareToken")) {
            throw new IllegalStateException("mushe contribution request creation returned no id/shareToken");
        }
        return new CampaignMoneyLink(
                UUID.fromString(created.get("id").asText()),
                created.get("shareToken").asText());
    }

    /** Cancel the campaign's contribution request and refund donors (mushe refund engine). */
    public void cancelAndRefund(UUID tenantId, UUID contributionRequestId) {
        post("/internal/v1/bill-contributions/" + contributionRequestId + "/cancel-and-refund",
                Map.of(), tenantId);
    }

    /** Release escrow funds to the beneficiary (the case layer gates on verified-ACTIVE). */
    public void release(UUID tenantId, UUID contributionRequestId, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        if (amount != null) {
            body.put("amount", amount);
        }
        post("/internal/v1/bill-contributions/" + contributionRequestId + "/release", body, tenantId);
    }

    private JsonNode post(String path, Map<String, Object> body, UUID tenantId) {
        return restClient.post()
                .uri(baseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Tenant-ID", tenantId.toString());
                    headers.set("X-Pod-ID", "national-spine");
                    headers.set("X-Request-ID", UUID.randomUUID().toString());
                    headers.set("X-Correlation-ID", UUID.randomUUID().toString());
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());
                    String bearer = currentBearer();
                    if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer);
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private static String currentBearer() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                return request.getHeader(HttpHeaders.AUTHORIZATION);
            }
        } catch (Exception ignored) {
            // Outside a request — proceed without a bearer.
        }
        return null;
    }
}
