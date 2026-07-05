package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP client for the WS-D workforce-governance employment-match endpoint
 * (frozen sibling contract: POST /internal/v1/workforce-governance/employment/match
 * {ecNumber, healthId?} → {matchStatus, ...}).
 *
 * <p>This client is only exercised behind the {@code impilo.features.ec-matching}
 * flag (default OFF) because the downstream lane may not be deployed yet.
 * IATG WS-F owns this file; the richer BFF {@code WorkforceGovernanceClient}
 * belongs to WS-D and is deliberately not referenced here.</p>
 */
@Component
public class WorkforceEmploymentMatchClient {

    private static final Logger log = LoggerFactory.getLogger(WorkforceEmploymentMatchClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WorkforceEmploymentMatchClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.workforce-governance-base-url:http://localhost:8250}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Match an EC (employment) number against the workforce-governance
     * employment registry. The EC number itself is never logged.
     */
    public JsonNode matchEmployment(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/workforce-governance/employment/match";
        log.info("WORKFORCE: employment match requested");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }
}
