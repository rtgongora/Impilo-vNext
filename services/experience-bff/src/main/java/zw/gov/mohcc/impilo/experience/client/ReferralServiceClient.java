package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for referral-service (the referral movement system of record, port 8399).
 *
 * <p>The BFF composes a citizen-safe referral <em>movement</em> view from this read; referral-service
 * remains the sole owner of referral truth and lifecycle. This client only reads — the citizen lane
 * never mutates a referral.</p>
 */
@Component
public class ReferralServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ReferralServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ReferralServiceClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.orchestration-backlog.referral-service-base-url:http://localhost:8399}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Fetch a single referral envelope by id (tenant-scoped in the service). */
    public JsonNode getReferral(String referralId) {
        String url = baseUrl + "/internal/v1/referrals/" + referralId;
        log.debug("Referral: get referral={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }
}
