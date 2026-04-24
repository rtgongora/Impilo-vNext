package zw.gov.mohcc.impilo.varapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and reads MusheX payment intents (financial truth for council-regulated fees).
 */
@Component
public class MusheXClient {

    private static final Logger log = LoggerFactory.getLogger(MusheXClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MusheXClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    public boolean isEnabled() {
        VarapiProperties.MusheXProperties p = varapiProperties.getMushex();
        return p.isEnabled() && p.getBaseUrl() != null && !p.getBaseUrl().isBlank();
    }

    /**
     * POST /mushex/v1/payment-intents — returns intent id or null on failure.
     */
    public String createPaymentIntent(
            String sourceType,
            String sourceId,
            BigDecimal amount,
            String currency,
            UUID facilityId,
            String idempotencyKey,
            String metadataJson) {
        if (!isEnabled()) {
            log.debug("MusheX client disabled — skipping intent create");
            return null;
        }
        TrustContext ctx = TrustContextHolder.require();
        String base = trimSlash(varapiProperties.getMushex().getBaseUrl());
        String url = base + "/mushex/v1/payment-intents";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        body.put("amount", amount);
        body.put("currency", currency != null ? currency : "USD");
        if (facilityId != null) {
            body.put("facilityId", facilityId.toString());
        }
        body.put("idempotencyKey", idempotencyKey);
        body.put("metadata", metadataJson);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyTrustHeaders(headers, ctx);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = response.getBody();
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            JsonNode intentId = data.get("intentId");
            return intentId != null && !intentId.isNull() ? intentId.asText() : null;
        } catch (RestClientException e) {
            log.warn("MusheX intent create failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * GET /mushex/v1/payment-intents/{id} — returns body {@code data} node or null.
     */
    public JsonNode getPaymentIntent(String intentId) {
        if (!isEnabled()) {
            return null;
        }
        TrustContext ctx = TrustContextHolder.require();
        String base = trimSlash(varapiProperties.getMushex().getBaseUrl());
        String url = base + "/mushex/v1/payment-intents/" + intentId;

        HttpHeaders headers = new HttpHeaders();
        applyTrustHeaders(headers, ctx);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode data = response.getBody().path("data");
            return data.isMissingNode() || data.isNull() ? null : data;
        } catch (RestClientException e) {
            log.warn("MusheX intent get failed for {}: {}", intentId, e.getMessage());
            return null;
        }
    }

    private static void applyTrustHeaders(HttpHeaders headers, TrustContext ctx) {
        if (ctx.tenantId() != null) {
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        }
        if (ctx.correlationId() != null) {
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        }
        if (ctx.actorId() != null) {
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
        }
        if (ctx.actorType() != null) {
            headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
        }
        headers.set(TrustContext.H_PURPOSE_OF_USE,
                ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "REGISTRY_ADMIN");
        headers.set(TrustContext.H_DEVICE_FINGERPRINT,
                ctx.deviceFingerprint() != null ? ctx.deviceFingerprint() : "varapi");
        if (ctx.facilityId() != null) {
            headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
        }
        headers.set(TrustContext.H_ACCESS_MODE,
                ctx.mode() != null ? ctx.mode().name() : "INTERNAL");
    }

    public String buildMetadataJson(long obligationId, Long applicationId, String providerPublicId) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider_id", providerPublicId);
            m.put("providerId", providerPublicId);
            m.put("obligation_id", obligationId);
            if (applicationId != null) {
                m.put("application_id", applicationId);
            }
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize obligation metadata", e);
        }
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
