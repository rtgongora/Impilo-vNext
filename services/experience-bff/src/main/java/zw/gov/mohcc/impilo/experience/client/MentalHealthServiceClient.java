package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for mental-health-service (port 8397) — the accepting counterparty for a
 * psychiatric emergency handover (W13). Trust headers are forwarded automatically by
 * {@code serviceRestTemplate}'s header-forwarding interceptor, same as every other client in this
 * package.
 *
 * <p>Scoped to the referral acceptance surface for this wave — the fuller clinical-record surface
 * (assessment, risk formulation, safety plan, involuntary episode, restraint events, admission
 * requests, follow-up) is deferred to W15's experience layer, matching this pack's own convention
 * of shipping backend + a minimal acceptance-side BFF surface first and the fuller UI/BFF surface
 * once the workspace primitives that would host it exist.</p>
 */
@Component
public class MentalHealthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MentalHealthServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MentalHealthServiceClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.mental-health-base-url:http://localhost:8397}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    public JsonNode intakeReferral(JsonNode body) {
        log.debug("Mental health: intake referral");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/mental-health/referrals", body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getReferral(String referralId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/mental-health/referrals/" + referralId, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listReferrals(String status) {
        String url = baseUrl + "/internal/v1/mental-health/referrals?status="
                + (status != null ? status : "PENDING");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode acceptReferral(String referralId, JsonNode body) {
        log.debug("Mental health: accept referral={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/mental-health/referrals/" + referralId + "/accept", body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode declineReferral(String referralId, JsonNode body) {
        log.debug("Mental health: decline referral={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/mental-health/referrals/" + referralId + "/decline", body, JsonNode.class);
        return response.getBody();
    }
}
