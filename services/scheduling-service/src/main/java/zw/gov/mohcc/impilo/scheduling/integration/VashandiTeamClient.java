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
 * Reads theatre case-team confirmation from VASHANDI (the workforce SoR). Returns
 * UNKNOWN when unreachable so conflict detection degrades to a soft warning.
 */
@Component
public class VashandiTeamClient {

    private static final Logger log = LoggerFactory.getLogger(VashandiTeamClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public VashandiTeamClient(
            @Value("${scheduling.integration.vashandi-base-url:http://localhost:8167}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Whether a theatre team is CONFIRMED. UNKNOWN when vashandi unreachable. */
    public TeamState teamState(UUID vashandiTeamId) {
        if (vashandiTeamId == null) {
            return TeamState.UNKNOWN;
        }
        try {
            String url = baseUrl + "/v1/internal/vashandi/theatre-teams/" + vashandiTeamId;
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(serviceHeaders()), JsonNode.class);
            JsonNode root = resp.getBody();
            if (root == null) {
                return TeamState.UNKNOWN;
            }
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data.has("status")) {
                return "CONFIRMED".equalsIgnoreCase(data.get("status").asText())
                        ? TeamState.CONFIRMED : TeamState.NOT_CONFIRMED;
            }
            return TeamState.UNKNOWN;
        } catch (Exception e) {
            log.debug("VASHANDI team state unknown for {}: {}", vashandiTeamId, e.getMessage());
            return TeamState.UNKNOWN;
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

    public enum TeamState {
        CONFIRMED, NOT_CONFIRMED, UNKNOWN
    }
}
