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
 * Integration service for the MUSHEX Costing and Payment Engine.
 *
 * <p>MUSHEX handles all financial aspects of patient care: chargeable
 * event tracking, cost calculation, payment processing, and exemption
 * management. This integration allows PCT to emit chargeable events
 * (e.g. consultation fees, bed charges, procedure fees) and query
 * payment status for discharge clearance.</p>
 *
 * <p>All external calls degrade gracefully: if MUSHEX is unavailable,
 * the failure is logged and an empty/default response is returned.
 * Financial events are also published via the outbox pattern for
 * guaranteed delivery.</p>
 */
@Service
public class MushexIntegration {

    private static final Logger log = LoggerFactory.getLogger(MushexIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MushexIntegration(RestTemplate restTemplate,
                             @Value("${pct.integration.mushex.base-url:http://localhost:8085}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Emit a chargeable event to MUSHEX for a patient journey.
     *
     * <p>Chargeable events represent billable activities during a patient
     * visit (e.g. CONSULTATION, BED_DAY, PROCEDURE, LAB_TEST, IMAGING).
     * MUSHEX uses these events to compute the patient's bill.</p>
     *
     * @param journeyId the PCT journey this event belongs to
     * @param eventType the type of chargeable event
     * @param payload   additional event data as a JSON string (e.g. quantity, procedure code)
     * @return a map containing the MUSHEX event acknowledgement, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> emitChargeableEvent(String journeyId, String eventType, String payload) {
        try {
            String url = baseUrl + "/v1/chargeable-events";
            Map<String, Object> body = Map.of(
                    "journeyId", journeyId,
                    "eventType", eventType,
                    "payload", payload
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("MUSHEX chargeable event emitted: journey={}, type={}", journeyId, eventType);
                return response.getBody();
            }

            log.warn("MUSHEX returned non-success status {} when emitting chargeable event for journey {}",
                    response.getStatusCode(), journeyId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("MUSHEX unavailable when emitting chargeable event for journey {}: {}",
                    journeyId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Query the payment status for a patient journey.
     *
     * <p>Returns the current payment status which may be one of:
     * PENDING, PARTIAL, CLEARED, EXEMPTED, WAIVED. This status
     * is used by the discharge workflow to determine if the PAYMENT
     * blocker can be cleared.</p>
     *
     * @param journeyId the PCT journey to query
     * @return a map containing the payment status, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPaymentStatus(String journeyId) {
        try {
            String url = baseUrl + "/v1/payment-status/" + journeyId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("MUSHEX payment status retrieved for journey {}", journeyId);
                return response.getBody();
            }

            log.warn("MUSHEX returned non-success status {} when querying payment for journey {}",
                    response.getStatusCode(), journeyId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("MUSHEX unavailable when querying payment for journey {}: {}",
                    journeyId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
