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
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Nhume is dispatch SoR for marketplace human/community-delivery orders.
 *
 * <p>{@code FulfillmentService.routeOrder} calls this — behind
 * {@code msika-flow.fulfillment.nhume-enabled} — after a delivery plan is created for
 * a non-pickup delivery mode. Failure is swallowed to a warning: the manual
 * out-for-delivery / mark-delivered endpoints remain the documented recovery path, so
 * a Nhume outage never blocks the order from progressing.</p>
 */
@Service
public class NhumeClient {

    private static final Logger log = LoggerFactory.getLogger(NhumeClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record NhumeDeliveryCreated(String deliveryId, String status) {}

    public NhumeClient(ObjectMapper objectMapper,
                       @Value("${msika-flow.integration.nhume-url:http://nhume-service:8210}") String nhumeBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(nhumeBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * POST /api/v1/nhume/deliveries. Never throws — a Nhume-side failure is logged and
     * treated as "no dispatch id yet", not an order-blocking error.
     */
    public Optional<NhumeDeliveryCreated> createMarketplaceDelivery(OrderEntity order,
                                                                     String mushexPaymentIntentId,
                                                                     HttpServletRequest inbound) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("delivery_type", "MARKETPLACE");
            body.put("request_source", "MSIKA_FLOW");
            body.put("requesting_actor_id", order.getActorId());
            body.put("requesting_actor_type", order.getActorType() != null ? order.getActorType().name() : null);
            if (order.getFacilityId() != null) {
                body.put("requesting_facility_id", order.getFacilityId().toString());
            }
            body.put("marketplace_order_ref", order.getOrderId());

            Map<String, Object> origin = new LinkedHashMap<>();
            if (order.getVendorId() != null) {
                origin.put("kind", "VENDOR");
                origin.put("ref", order.getVendorId().toString());
            } else if (order.getFacilityId() != null) {
                origin.put("kind", "FACILITY");
                origin.put("ref", order.getFacilityId().toString());
            }
            body.put("origin", origin);

            Map<String, Object> destination = new LinkedHashMap<>();
            destination.put("kind", "PATIENT");
            destination.put("ref", order.getPatientCpid());
            body.put("destination", destination);

            Map<String, Object> recipient = new LinkedHashMap<>();
            recipient.put("kind", "PATIENT");
            recipient.put("ref", order.getPatientCpid());
            body.put("recipient", recipient);

            if (mushexPaymentIntentId != null) {
                body.put("payment_path", "PREPAID");
                body.put("payment_reference", mushexPaymentIntentId);
            }
            body.put("submit_immediately", true);

            Map<String, Object> links = new LinkedHashMap<>();
            links.put("msikaFlowOrderRef", order.getOrderId());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("links", links);
            body.put("metadata", metadata);

            ResponseEntity<String> response = restClient.post()
                    .uri("/api/v1/nhume/deliveries")
                    .headers(h -> copyTrustHeaders(inbound, h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Nhume delivery create failed for order {}: HTTP {}", order.getOrderId(), response.getStatusCode());
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            String deliveryId = data.hasNonNull("delivery_id") ? data.get("delivery_id").asText()
                    : data.hasNonNull("deliveryId") ? data.get("deliveryId").asText() : null;
            if (deliveryId == null || deliveryId.isBlank()) {
                log.warn("Nhume delivery create response missing delivery_id for order {}", order.getOrderId());
                return Optional.empty();
            }
            String status = data.hasNonNull("status") ? data.get("status").asText() : "CREATED";
            return Optional.of(new NhumeDeliveryCreated(deliveryId, status));
        } catch (Exception e) {
            log.warn("Nhume delivery create failed for order {}: {}", order.getOrderId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** MushexClient {@code copyTrustHeaders} idiom, reused for the Nhume hop. */
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
