package zw.gov.mohcc.impilo.zibo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Integration service for communicating with the TUSO (Facility Registry) service.
 *
 * <p>Uses RestTemplate with graceful degradation -- if TUSO is unavailable or
 * returns an error, the integration returns an empty result rather than
 * propagating the failure. This ensures that ZIBO operations can proceed
 * even when TUSO is temporarily unreachable.</p>
 *
 * <p>Used by the GovernanceService to look up facility profiles when
 * determining default pack assignments based on facility type.</p>
 */
@Service
public class TusoIntegration {

    private static final Logger log = LoggerFactory.getLogger(TusoIntegration.class);

    private final RestTemplate restTemplate;
    private final String tusoBaseUrl;

    public TusoIntegration(
            @Value("${zibo.integration.tuso-base-url:http://localhost:8084}") String tusoBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.tusoBaseUrl = tusoBaseUrl;
    }

    /**
     * Constructor for testing with a custom RestTemplate.
     *
     * @param restTemplate the RestTemplate to use
     * @param tusoBaseUrl  the base URL for the TUSO service
     */
    TusoIntegration(RestTemplate restTemplate, String tusoBaseUrl) {
        this.restTemplate = restTemplate;
        this.tusoBaseUrl = tusoBaseUrl;
    }

    /**
     * Look up a facility profile from TUSO by facility ID.
     *
     * <p>Returns a map containing facility metadata such as facility type,
     * district, province, and other attributes. Returns an empty map if
     * TUSO is unavailable or the facility is not found.</p>
     *
     * @param facilityId the facility identifier to look up
     * @return a map of facility profile attributes, or an empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> lookupFacilityProfile(UUID facilityId) {
        if (facilityId == null) {
            return Collections.emptyMap();
        }

        String url = tusoBaseUrl + "/v1/facilities/" + facilityId;

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                // The TUSO API wraps data in an ApiResponse envelope
                if (body.containsKey("data") && body.get("data") instanceof Map) {
                    return (Map<String, Object>) body.get("data");
                }

                return body;
            }

            log.warn("TUSO returned non-success status {} for facility {}",
                    response.getStatusCode(), facilityId);
            return Collections.emptyMap();

        } catch (Exception e) {
            log.warn("Failed to look up facility {} from TUSO: {} ({})",
                    facilityId, e.getMessage(), e.getClass().getSimpleName());
            return Collections.emptyMap();
        }
    }
}
