package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;

/**
 * HTTP client for the Clinical Knowledge Platform (EDLIZ-aligned rules, assistant, pathways).
 */
@Component
public class ClinicalKnowledgePlatformClient {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKnowledgePlatformClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ClinicalKnowledgePlatformClient(
            RestTemplate serviceRestTemplate,
            ClinicalPlatformProperties clinicalPlatform) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = clinicalPlatform.baseUrl();
    }

    public JsonNode assistantAsk(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/assistant/ask";
        log.debug("Clinical platform: assistant ask");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getTrace(String traceId) {
        String url = baseUrl + "/internal/v1/clinical/assistant/traces/" + traceId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode prescribingEvaluate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/prescribing/evaluate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listPathways() {
        String url = baseUrl + "/internal/v1/clinical/pathways";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
