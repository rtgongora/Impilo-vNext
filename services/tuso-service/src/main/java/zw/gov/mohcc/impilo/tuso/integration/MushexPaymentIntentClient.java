package zw.gov.mohcc.impilo.tuso.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MusheX payment-intent client for HPA registration fees. A synchronous HTTP
 * handoff (the estate pattern for intent creation — Kafka consumer threads carry
 * no TrustContext) porting varapi's proven ProviderPaymentObligation client:
 * forwards trust headers, sends {@code sourceType=HPA_FACILITY_FEE}, and returns
 * the minted intent id.
 *
 * <p>In sandbox mode ({@code tuso.mushex.sandbox-simulation=true}, rig/preview
 * only) the metadata carries the Impilo-simulation keys so MusheX auto-settles
 * the intent — never enabled in production.</p>
 */
@Service
public class MushexPaymentIntentClient {

    private static final Logger log = LoggerFactory.getLogger(MushexPaymentIntentClient.class);

    private final TusoProperties tusoProperties;
    private final RestClient restClient;

    public MushexPaymentIntentClient(TusoProperties tusoProperties, RestClient.Builder restClientBuilder) {
        this.tusoProperties = tusoProperties;
        this.restClient = restClientBuilder
                .baseUrl(trimSlash(tusoProperties.getMushex().getBaseUrl()))
                .build();
    }

    public boolean isEnabled() {
        TusoProperties.MushexProperties p = tusoProperties.getMushex();
        return p.isEnabled() && p.getBaseUrl() != null && !p.getBaseUrl().isBlank();
    }

    /** POST /mushex/v1/payment-intents — returns the minted intent id, or null on failure/disabled. */
    public String createPaymentIntent(String sourceId, BigDecimal amount, String currency, String idempotencyKey) {
        if (!isEnabled()) {
            log.debug("MusheX client disabled — skipping HPA fee intent create");
            return null;
        }
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", "HPA_FACILITY_FEE");
        body.put("sourceId", sourceId);
        body.put("amount", amount);
        body.put("currency", currency != null ? currency : "USD");
        if (ctx.facilityId() != null) {
            body.put("facilityId", ctx.facilityId().toString());
        }
        body.put("idempotencyKey", idempotencyKey);
        body.put("metadata", buildMetadata(sourceId));
        try {
            JsonNode root = restClient.post()
                    .uri("/mushex/v1/payment-intents")
                    .headers(h -> applyTrustHeaders(h, ctx))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) return null;
            JsonNode data = root.path("data");
            JsonNode intentId = data.get("intentId");
            return intentId != null && !intentId.isNull() ? intentId.asText() : null;
        } catch (RestClientException e) {
            log.warn("MusheX HPA fee intent create failed for {}: {}", sourceId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildMetadata(String applicationId) {
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("hpa_application_id", applicationId);
        md.put("source_instrument", "SI 78 of 2017");
        if (tusoProperties.getMushex().isSandboxSimulation()) {
            // Rig/preview escape hatch — MusheX sandbox auto-settles.
            md.put("impilo_simulation", true);
            md.put("simulation_outcome", "paid");
        }
        return md;
    }

    private static void applyTrustHeaders(HttpHeaders headers, TrustContext ctx) {
        if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
        if (ctx.actorType() != null) headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
        headers.set(TrustContext.H_PURPOSE_OF_USE,
                ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "REGULATION");
        if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
        headers.set("X-Pod-ID", "national-spine");
        headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
    }

    private static String trimSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
