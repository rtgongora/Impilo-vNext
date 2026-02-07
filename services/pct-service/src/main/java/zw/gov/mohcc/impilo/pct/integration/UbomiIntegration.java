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
 * Integration service for the UBOMI Civil Registration and Vital Statistics system.
 *
 * <p>UBOMI is the national CRVS (Civil Registration and Vital Statistics)
 * system in Zimbabwe. When a patient death is recorded in a facility,
 * PCT notifies UBOMI for civil registration purposes. This integration
 * handles the death notification lifecycle including submission and
 * status tracking.</p>
 *
 * <p>All external calls degrade gracefully: if UBOMI is unavailable, the
 * failure is logged and an empty/default response is returned. The death
 * notification is also published via the outbox pattern for guaranteed
 * delivery through asynchronous retry.</p>
 */
@Service
public class UbomiIntegration {

    private static final Logger log = LoggerFactory.getLogger(UbomiIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UbomiIntegration(RestTemplate restTemplate,
                            @Value("${pct.integration.ubomi.base-url:http://localhost:8086}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Notify UBOMI of a patient death for civil registration.
     *
     * <p>Submits the death details to UBOMI's notification endpoint.
     * On success, returns a map containing the UBOMI notification
     * reference which should be stored on the death case entity for
     * subsequent status queries.</p>
     *
     * @param journeyId    the PCT journey in which the death occurred
     * @param deathPayload the death notification data as a JSON string
     * @return a map containing the UBOMI notification reference, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> notifyDeath(String journeyId, String deathPayload) {
        try {
            String url = baseUrl + "/v1/death-notifications";
            Map<String, Object> body = Map.of(
                    "journeyId", journeyId,
                    "payload", deathPayload
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("UBOMI death notification submitted for journey {}", journeyId);
                return response.getBody();
            }

            log.warn("UBOMI returned non-success status {} when notifying death for journey {}",
                    response.getStatusCode(), journeyId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("UBOMI unavailable when notifying death for journey {}: {}",
                    journeyId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Query the status of a death notification in UBOMI.
     *
     * <p>Returns the current processing status of the death notification
     * in the CRVS system. Possible statuses include: SUBMITTED,
     * ACKNOWLEDGED, UNDER_REVIEW, REGISTERED, REJECTED.</p>
     *
     * @param notificationId the UBOMI notification reference
     * @return a map containing the notification status details, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeathNotificationStatus(String notificationId) {
        try {
            String url = baseUrl + "/v1/death-notifications/" + notificationId + "/status";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("UBOMI notification status retrieved for {}", notificationId);
                return response.getBody();
            }

            log.warn("UBOMI returned non-success status {} when querying notification {}",
                    response.getStatusCode(), notificationId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("UBOMI unavailable when querying notification {}: {}",
                    notificationId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
