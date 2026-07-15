package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Integration service for the OROS Order Registry Service.
 *
 * <p>OROS manages clinical orders (lab tests, radiology, pharmacy) across
 * the facility. This integration allows PCT to submit orders on behalf of
 * a patient journey and query order status for task tracking.</p>
 *
 * <p>All external calls degrade gracefully: if OROS is unavailable, the
 * failure is logged and an empty/default response is returned so that the
 * calling workflow can continue without blocking the patient journey.</p>
 */
@Service
public class OrosIntegration {

    private static final Logger log = LoggerFactory.getLogger(OrosIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosIntegration(RestTemplate restTemplate,
                           @Value("${pct.integration.oros.base-url:http://localhost:8089}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Submit an order to OROS for a patient journey.
     *
     * <p>The order payload should contain the order details in the format
     * expected by the OROS API (order type, items, patient reference, etc.).
     * The payload is passed as a raw JSON string for maximum flexibility.</p>
     *
     * @param journeyId    the PCT journey this order is associated with
     * @param orderPayload the order data as a JSON string
     * @return a map containing the OROS order reference, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitOrder(String journeyId, String orderPayload) {
        try {
            String url = baseUrl + "/v1/orders";
            Map<String, Object> body = Map.of(
                    "journeyId", journeyId,
                    "payload", orderPayload
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("OROS order submitted for journey {}", journeyId);
                return response.getBody();
            }

            log.warn("OROS returned non-success status {} when submitting order for journey {}",
                    response.getStatusCode(), journeyId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("OROS unavailable when submitting order for journey {}: {}",
                    journeyId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Query the status of an order in OROS.
     *
     * @param orderId the OROS order identifier
     * @return a map containing the order status details, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderStatus(String orderId) {
        try {
            String url = baseUrl + "/v1/orders/" + orderId + "/status";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("OROS order status retrieved for order {}", orderId);
                return response.getBody();
            }

            log.warn("OROS returned non-success status {} when querying order {}",
                    response.getStatusCode(), orderId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("OROS unavailable when querying order {}: {}", orderId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Place a trauma ED diagnostic order on OROS with {@code encounter_ref = trauma_episode_id}
     * (uses the OROS API; no OROS code change). Returns the OROS order id, or null on failure.
     */
    @SuppressWarnings("unchecked")
    public String placeOrder(String orderType, String patientCpid, String encounterRef,
                             String priority, String clinicalNotes) {
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("orderType", orderType);
            if (priority != null) body.put("priority", priority);
            body.put("patientCpid", patientCpid);
            if (encounterRef != null) body.put("encounterRef", encounterRef);
            if (clinicalNotes != null) body.put("clinicalNotes", clinicalNotes);
            ResponseEntity<Map> response = restTemplate.exchange(baseUrl + "/v1/orders",
                    HttpMethod.POST, new HttpEntity<>(body, trustHeaders()), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().getOrDefault("data", response.getBody());
                Object id = data instanceof Map ? ((Map<String, Object>) data).get("orderId") : null;
                return id != null ? id.toString() : null;
            }
            return null;
        } catch (RestClientException e) {
            log.warn("OROS unavailable placing order for patient {}: {}", patientCpid, e.getMessage());
            return null;
        }
    }

    /** Read an OROS order (for the trauma read-through: resolve its encounter_ref = trauma episode). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrder(String orderId) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(baseUrl + "/v1/orders/" + orderId,
                    HttpMethod.GET, new HttpEntity<>(trustHeaders()), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().getOrDefault("data", response.getBody());
                return data instanceof Map ? (Map<String, Object>) data : response.getBody();
            }
            return Collections.emptyMap();
        } catch (RestClientException e) {
            log.warn("OROS unavailable reading order {}: {}", orderId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Acknowledge a critical result via OROS's existing order-scoped ack endpoint
     * ({@code POST /v1/orders/{orderId}/ack}, ackType=CRITICAL → oros_acknowledgements). Uses the
     * OROS API only (no OROS code change). Returns true on success. Never throws.
     */
    public boolean acknowledgeCriticalOrder(String orderId, String reason) {
        try {
            Map<String, Object> body = Map.of("ackType", "CRITICAL",
                    "notes", reason == null ? "trauma ED critical-result acknowledgement" : reason);
            ResponseEntity<Map> response = restTemplate.exchange(baseUrl + "/v1/orders/" + orderId + "/ack",
                    HttpMethod.POST, new HttpEntity<>(body, trustHeaders()), Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("OROS unavailable acking critical order {}: {}", orderId, e.getMessage());
            return false;
        }
    }

    /** Propagate the inbound trust context so OROS (tenant-scoped) resolves the order. */
    private HttpHeaders trustHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) h.set("X-Tenant-ID", ctx.tenantId().toString());
            if (ctx.actorId() != null) h.set("X-Actor-ID", ctx.actorId());
            h.set("X-Correlation-ID", ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) h.set("X-Facility-ID", ctx.facilityId().toString());
            if (ctx.purposeOfUse() != null) h.set("X-Purpose-Of-Use", ctx.purposeOfUse());
        } catch (IllegalStateException e) {
            h.set("X-Correlation-ID", UUID.randomUUID().toString());
        }
        h.set("X-Actor-Type", "SERVICE");
        return h;
    }
}
