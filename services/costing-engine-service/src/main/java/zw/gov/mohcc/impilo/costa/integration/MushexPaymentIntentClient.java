package zw.gov.mohcc.impilo.costa.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Forwards trust headers from the inbound servlet request and calls MusheX payment-intent APIs.
 */
@Component
public class MushexPaymentIntentClient {

    private final RestClient mushexRestClient;
    private final ObjectMapper objectMapper;

    public record MushexIntentCreated(String intentId, String status) {}

    public MushexPaymentIntentClient(@Qualifier("mushexRestClient") RestClient mushexRestClient,
                                     ObjectMapper objectMapper) {
        this.mushexRestClient = mushexRestClient;
        this.objectMapper = objectMapper;
    }

    public MushexIntentCreated createPaymentIntent(HttpServletRequest inbound,
                                       String sourceType,
                                       String sourceId,
                                       String amount,
                                       String currency,
                                       String facilityId,
                                       String idempotencyKey,
                                       String metadataJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        body.put("amount", new BigDecimal(amount));
        body.put("currency", currency != null ? currency : "USD");
        if (facilityId != null) {
            body.put("facilityId", facilityId);
        }
        body.put("idempotencyKey", idempotencyKey);
        body.put("metadata", metadataJson);

        ResponseEntity<String> response = mushexRestClient.post()
                .uri("/mushex/v1/payment-intents")
                .headers(h -> copyTrustHeaders(inbound, h))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("MusheX payment intent create failed: HTTP " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            if (data.hasNonNull("intentId")) {
                String st = data.path("status").asText("CREATED");
                return new MushexIntentCreated(data.get("intentId").asText(), st);
            }
            throw new IllegalStateException("MusheX response missing intentId");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MusheX response parse failed", e);
        }
    }

    private static void copyTrustHeaders(HttpServletRequest inbound, org.springframework.http.HttpHeaders target) {
        if (inbound == null) {
            return;
        }
        copyIfPresent(inbound, target, TrustContext.H_TENANT_ID);
        copyIfPresent(inbound, target, TrustContext.H_ACTOR_ID);
        copyIfPresent(inbound, target, TrustContext.H_ACTOR_TYPE);
        copyIfPresent(inbound, target, TrustContext.H_PURPOSE_OF_USE);
        copyIfPresent(inbound, target, TrustContext.H_DEVICE_FINGERPRINT);
        copyIfPresent(inbound, target, TrustContext.H_CORRELATION_ID);
        copyIfPresent(inbound, target, TrustContext.H_FACILITY_ID);
        copyIfPresent(inbound, target, TrustContext.H_WORKSPACE_ID);
        copyIfPresent(inbound, target, TrustContext.H_SHIFT_ID);
        copyIfPresent(inbound, target, TrustContext.H_ACCESS_MODE);
        copyIfPresent(inbound, target, "Authorization");
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound,
                                      org.springframework.http.HttpHeaders target,
                                      String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
