package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

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
}
