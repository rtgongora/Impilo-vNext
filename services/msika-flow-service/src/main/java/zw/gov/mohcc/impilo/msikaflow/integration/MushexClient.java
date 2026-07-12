package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real MusheX payment-rail client — a port of COSTA's {@code MushexPaymentIntentClient}
 * precedent (synchronous HTTP intent handoff with trust headers forwarded from the
 * inbound servlet request).
 *
 * <p>Replaces the previous stub which fabricated {@code mpi_<orderId>} intent ids
 * locally: those ids never existed in MusheX, so {@code mushex.payment.status.changed}
 * events could never match a settlement and no order could ever reach PAID through the
 * real rail. Fail-loud by design — an order without a real intent must stay PRICED.</p>
 */
@Service
public class MushexClient {

    private static final Logger log = LoggerFactory.getLogger(MushexClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record MushexIntentCreated(String intentId, String status) {}
    public record MushexRefundCreated(String refundId, String status) {}

    public MushexClient(ObjectMapper objectMapper,
                        @Value("${msika-flow.integration.mushex-url:http://localhost:8102}") String mushexBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(mushexBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * POST /mushex/v1/payment-intents with {@code sourceType=MSIKA_ORDER} and the
     * stable idempotency key {@code MSIKA_ORDER:<orderId>} (matching what the retired
     * mushex-side Kafka consumer used, so replays cannot double-create intents).
     */
    public MushexIntentCreated createPaymentIntent(HttpServletRequest inbound,
                                                   String orderId,
                                                   BigDecimal amount,
                                                   String currency,
                                                   String facilityId,
                                                   String metadataJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", "MSIKA_ORDER");
        body.put("sourceId", orderId);
        body.put("amount", amount);
        body.put("currency", currency != null ? currency : "USD");
        if (facilityId != null) {
            body.put("facilityId", facilityId);
        }
        body.put("idempotencyKey", "MSIKA_ORDER:" + orderId);
        body.put("metadata", metadataJson);

        ResponseEntity<String> response = restClient.post()
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
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (data.hasNonNull("intentId")) {
                return new MushexIntentCreated(data.get("intentId").asText(),
                        data.path("status").asText("CREATED"));
            }
            throw new IllegalStateException("MusheX response missing intentId");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MusheX response parse failed", e);
        }
    }

    /**
     * POST /mushex/v1/payment-intents/{intentId}/refund. The step-up header
     * ({@code X-Step-Up-Token}) is forwarded so MusheX's large-refund gate sees the
     * caller's step-up, not a synthesized identity.
     */
    public MushexRefundCreated requestRefund(HttpServletRequest inbound,
                                             String mushexPaymentIntentId,
                                             BigDecimal amount,
                                             String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        if (reason != null) {
            body.put("reason", reason);
        }

        ResponseEntity<String> response = restClient.post()
                .uri("/mushex/v1/payment-intents/{id}/refund", mushexPaymentIntentId)
                .headers(h -> copyTrustHeaders(inbound, h))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("MusheX refund request failed: HTTP " + response.getStatusCode());
        }
        try {
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (data.hasNonNull("refundId")) {
                return new MushexRefundCreated(data.get("refundId").asText(),
                        data.path("status").asText("PENDING"));
            }
            throw new IllegalStateException("MusheX refund response missing refundId");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MusheX refund response parse failed", e);
        }
    }

    /**
     * COSTA {@code copyTrustHeaders} idiom: forward the raw inbound trust headers plus
     * Authorization (msika-flow reads headers via {@link TrustHeaderExtractor}, it has
     * no TrustContextHolder), and mark the hop internal for Envoy.
     */
    private static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target) {
        if (inbound == null) {
            return;
        }
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_DEVICE_FINGERPRINT);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_WORKSPACE_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_SHIFT_ID);
        copyIfPresent(inbound, target, "X-Step-Up-Token");
        copyIfPresent(inbound, target, "Authorization");
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
