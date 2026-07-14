package zw.gov.mohcc.impilo.scheduling.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Reads clinical-space / instrument-set availability from TUSO (the resource
 * registry SoR). Availability is a real query when TUSO is reachable; when it is
 * not, the check returns UNKNOWN so conflict detection degrades to a soft warning
 * rather than a false block.
 */
@Component
public class TusoResourceClient {

    private static final Logger log = LoggerFactory.getLogger(TusoResourceClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public TusoResourceClient(@Value("${scheduling.integration.tuso-base-url:http://localhost:8084}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Availability of a theatre clinical space on a date. UNKNOWN when TUSO unreachable. */
    public Availability clinicalSpaceAvailability(UUID clinicalSpaceId, String date) {
        if (clinicalSpaceId == null) {
            return Availability.UNKNOWN;
        }
        try {
            String url = baseUrl + "/v1/internal/tuso/clinical-spaces/" + clinicalSpaceId + "/availability?date=" + date;
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(serviceHeaders()), JsonNode.class);
            JsonNode root = resp.getBody();
            if (root == null) {
                return Availability.UNKNOWN;
            }
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data.has("available")) {
                return data.get("available").asBoolean() ? Availability.AVAILABLE : Availability.UNAVAILABLE;
            }
            if (data.has("status")) {
                return "ACTIVE".equalsIgnoreCase(data.get("status").asText())
                        ? Availability.AVAILABLE : Availability.UNAVAILABLE;
            }
            return Availability.UNKNOWN;
        } catch (Exception e) {
            log.debug("TUSO clinical-space availability unknown for {}: {}", clinicalSpaceId, e.getMessage());
            return Availability.UNKNOWN;
        }
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Pod-ID", "national-spine");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("X-Access-Mode", "INTERNAL");
        headers.set("X-Service-Id", "scheduling-service");
        return headers;
    }

    public enum Availability {
        AVAILABLE, UNAVAILABLE, UNKNOWN
    }
}
