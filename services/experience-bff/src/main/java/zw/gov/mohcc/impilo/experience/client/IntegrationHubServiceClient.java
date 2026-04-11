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
 * HTTP client for integration-hub-service (routes, dispatch, dead letters, mapping templates).
 */
@Component
public class IntegrationHubServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IntegrationHubServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IntegrationHubServiceClient(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.integrationHubBaseUrl();
    }

    public JsonNode listRoutes() {
        String url = baseUrl + "/internal/v1/routes";
        log.debug("Integration hub: list routes");
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode upsertRoute(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/routes";
        log.info("Integration hub: upsert route");
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }

    public ResponseEntity<JsonNode> dispatch(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/dispatch";
        log.info("Integration hub: dispatch");
        return restTemplate.postForEntity(url, body, JsonNode.class);
    }

    public JsonNode listDeadLetters(int page, int size, Boolean resolved) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/deadletters")
                .queryParam("page", page)
                .queryParam("size", size);
        if (resolved != null) {
            b.queryParam("resolved", resolved);
        }
        return restTemplate.getForEntity(b.toUriString(), JsonNode.class).getBody();
    }

    public JsonNode replayDeadLetter(String id) {
        String url = baseUrl + "/internal/v1/deadletters/" + id + "/replay";
        log.info("Integration hub: replay dead letter id={}", id);
        return restTemplate.postForEntity(url, null, JsonNode.class).getBody();
    }

    public JsonNode listMappingTemplates() {
        String url = baseUrl + "/internal/v1/mapping-templates";
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode createMappingTemplate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mapping-templates";
        log.info("Integration hub: create mapping template");
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }
}
