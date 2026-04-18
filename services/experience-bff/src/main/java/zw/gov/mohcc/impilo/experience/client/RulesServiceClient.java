package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for rules-service (Health OS §11: Extension Points — governed rules).
 *
 * <p>Maps to {@code RuleController} at {@code /internal/v1/rules}. Trust headers are
 * forwarded by the BFF {@link ServiceClientConfig} RestTemplate interceptor.</p>
 */
@Component
public class RulesServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RulesServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RulesServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.rulesBaseUrl();
    }

    public JsonNode createRule(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/rules";
        log.debug("Rules: create rule");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listRules() {
        String url = baseUrl + "/internal/v1/rules";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode createVersion(String key, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/rules/" + key + "/versions";
        log.debug("Rules: create version key={}", key);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode activate(String key, int version) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/rules/" + key + "/activate")
                .queryParam("version", version)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return response.getBody();
    }

    public JsonNode deactivate(String key) {
        String url = baseUrl + "/internal/v1/rules/" + key + "/deactivate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return response.getBody();
    }

    public JsonNode evaluate(String key, Map<String, Object> facts) {
        String url = baseUrl + "/internal/v1/rules/" + key + "/evaluate";
        Map<String, Object> body = Map.of("facts", facts);
        log.debug("Rules: evaluate key={}", key);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    /** Evaluate using a raw JSON object (must include a {@code facts} object). */
    public JsonNode evaluateRaw(String key, JsonNode requestBody) {
        String url = baseUrl + "/internal/v1/rules/" + key + "/evaluate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestBody, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getActiveAlerts() {
        String url = baseUrl + "/internal/v1/rules/alerts/active";
        log.debug("Rules: fetching active CDS alerts");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }
}
