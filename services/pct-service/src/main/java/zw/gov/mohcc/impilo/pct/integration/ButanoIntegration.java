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
 * Integration service for the BUTANO Shared Health Record (HAPI FHIR).
 *
 * <p>BUTANO is the Impilo Shared Health Record built on HAPI FHIR R4. It
 * stores clinical data using CPID-only references (no PII). This integration
 * service handles communication between PCT and BUTANO for encounter shell
 * creation, IPS summary retrieval, and visit summary retrieval.</p>
 *
 * <p>All external calls degrade gracefully: if BUTANO is unavailable, the
 * failure is logged and an empty/default response is returned so that the
 * calling workflow can continue.</p>
 */
@Service
public class ButanoIntegration {

    private static final Logger log = LoggerFactory.getLogger(ButanoIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ButanoIntegration(RestTemplate restTemplate,
                             @Value("${pct.integration.butano.base-url:http://localhost:8090}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Create an encounter shell in BUTANO for a patient.
     *
     * <p>Sends a request to BUTANO to create a FHIR Encounter resource
     * with minimal data (patient CPID, encounter type). The full clinical
     * data is populated later by the clinician via the EHR.</p>
     *
     * @param cpid          the patient's Common Patient Identifier
     * @param encounterType the type of encounter (e.g. CONSULTATION, PROCEDURE)
     * @return a map containing the FHIR Encounter reference, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createEncounterShell(String cpid, String encounterType) {
        try {
            String url = baseUrl + "/fhir/Encounter";
            Map<String, Object> body = Map.of(
                    "resourceType", "Encounter",
                    "status", "in-progress",
                    "class", Map.of("code", encounterType),
                    "subject", Map.of("identifier", Map.of("value", cpid))
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("BUTANO encounter shell created for CPID {} (type={})", cpid, encounterType);
                return response.getBody();
            }

            log.warn("BUTANO returned non-success status {} when creating encounter shell for CPID {}",
                    response.getStatusCode(), cpid);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable when creating encounter shell for CPID {}: {}",
                    cpid, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Request an International Patient Summary (IPS) from BUTANO.
     *
     * <p>The IPS is a FHIR document Bundle containing a summary of the
     * patient's key clinical data (medications, allergies, conditions,
     * immunizations).</p>
     *
     * @param cpid the patient's Common Patient Identifier
     * @return the IPS summary as a map, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestIpsSummary(String cpid) {
        try {
            String url = baseUrl + "/fhir/Patient/$summary?identifier=" + cpid;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("BUTANO IPS summary retrieved for CPID {}", cpid);
                return response.getBody();
            }

            log.warn("BUTANO returned non-success status {} when requesting IPS for CPID {}",
                    response.getStatusCode(), cpid);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable when requesting IPS for CPID {}: {}", cpid, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Request a visit summary from BUTANO for a specific encounter.
     *
     * <p>The visit summary aggregates all clinical resources associated
     * with a single encounter (observations, conditions, procedures,
     * medication requests, etc.).</p>
     *
     * @param encounterId the BUTANO encounter reference identifier
     * @return the visit summary as a map, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestVisitSummary(String encounterId) {
        try {
            String url = baseUrl + "/fhir/Encounter/" + encounterId + "/$visit-summary";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("BUTANO visit summary retrieved for encounter {}", encounterId);
                return response.getBody();
            }

            log.warn("BUTANO returned non-success status {} when requesting visit summary for encounter {}",
                    response.getStatusCode(), encounterId);
            return Collections.emptyMap();

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable when requesting visit summary for encounter {}: {}",
                    encounterId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
