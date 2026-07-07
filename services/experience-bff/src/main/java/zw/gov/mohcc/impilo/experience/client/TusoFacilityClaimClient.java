package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP client for the TUSO facility administrator claim/appointment lane (IATG Wave 2, WS-E).
 *
 * <p>TUSO owns the facility and the administrator appointment. This client is used exclusively by
 * the BFF {@code FacilityClaimController}; the subject key is the canonical facility UUID.</p>
 */
@Component
public class TusoFacilityClaimClient {

    private static final Logger log = LoggerFactory.getLogger(TusoFacilityClaimClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoFacilityClaimClient(RestTemplate serviceRestTemplate,
                                   @Value("${impilo.services.tuso-base-url:http://localhost:8084}") String tusoBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = tusoBaseUrl;
    }

    /** Is the facility claimable (allowed on platform) and already administered? */
    public JsonNode eligibility(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/admin-claim/eligibility";
        log.info("TUSO: facility-admin-claim eligibility for facility={}", facilityUuid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Non-sensitive facility summary + claimability verdict. */
    public JsonNode preview(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/admin-claim/preview";
        log.info("TUSO: facility-admin-claim preview for facility={}", facilityUuid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** All facility-admin appointments for a facility (Facility Mode: pending claims + who administers it). */
    public JsonNode appointments(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/admin-claim/appointments";
        log.info("TUSO: facility-admin appointments for facility={}", facilityUuid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Submit a facility-administrator claim → creates a PENDING appointment. */
    public JsonNode submitClaim(String facilityUuid, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/admin-claim";
        log.info("TUSO: submitting facility-admin claim for facility={}", facilityUuid);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    /** Approve a PENDING appointment → ACTIVE. */
    public JsonNode approve(String appointmentId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facility-admin-appointments/" + enc(appointmentId) + "/approve";
        log.info("TUSO: approving facility-admin appointment={}", appointmentId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body != null ? body : Map.of()), JsonNode.class);
        return extractData(response);
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
