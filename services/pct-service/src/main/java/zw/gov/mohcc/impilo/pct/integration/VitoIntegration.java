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
 * Integration service for the VITO Patient Identity Management service.
 *
 * <p>VITO is the authoritative source for patient identity data (PII) in
 * the Impilo platform. While clinical data in BUTANO uses CPID-only
 * references, PCT needs to resolve patient demographics for display
 * purposes (name, date of birth, contact details). VITO provides
 * search and resolution endpoints for this purpose.</p>
 *
 * <p>All external calls degrade gracefully: if VITO is unavailable, the
 * failure is logged and an empty/default response is returned. Patient
 * identity is not cached in PCT to maintain the single-source-of-truth
 * for PII in VITO.</p>
 */
@Service
public class VitoIntegration {

    private static final Logger log = LoggerFactory.getLogger(VitoIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VitoIntegration(RestTemplate restTemplate,
                           @Value("${pct.integration.vito.base-url:http://localhost:8082}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Search for patients in VITO using a free-text query.
     *
     * <p>The query can match against patient name, national ID, CPID,
     * or phone number. Results are returned with minimal demographic
     * data suitable for selection/confirmation workflows.</p>
     *
     * @param query the search query string
     * @return a map containing search results, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> searchPatient(String query) {
        try {
            String url = baseUrl + "/v1/patients/search?q=" + query;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("VITO patient search completed for query '{}'", query);
                return response.getBody();
            }

            log.warn("VITO returned non-success status {} when searching for '{}'",
                    response.getStatusCode(), query);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("VITO unavailable when searching for '{}': {}", query, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Resolve a patient's demographic data by their CPID.
     *
     * <p>Returns the patient's core demographics including name, date of
     * birth, gender, and contact information. This is used for display
     * in the PCT UI and for populating clinical document headers.</p>
     *
     * @param cpid the patient's Common Patient Identifier
     * @return a map containing the patient demographics, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolvePatientByCpid(String cpid) {
        try {
            String url = baseUrl + "/v1/patients/cpid/" + cpid;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("VITO patient resolved for CPID {}", cpid);
                return response.getBody();
            }

            log.warn("VITO returned non-success status {} when resolving CPID {}",
                    response.getStatusCode(), cpid);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("VITO unavailable when resolving CPID {}: {}", cpid, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
