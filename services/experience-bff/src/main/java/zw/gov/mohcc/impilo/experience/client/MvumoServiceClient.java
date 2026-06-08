package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the MVUMO consent orchestration service.
 */
@Component
public class MvumoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MvumoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MvumoServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.mvumoBaseUrl();
    }

    public JsonNode createConsentRequest(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests";
        log.info("MVUMO: creating teleconsult consent request");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listTemplates() {
        String url = baseUrl + "/internal/v1/mvumo/templates";
        log.info("MVUMO: listing consent templates");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createTemplate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/templates";
        log.info("MVUMO: creating consent template");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateTemplate(String templateId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/templates/" + templateId;
        log.info("MVUMO: updating consent template {}", templateId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode createRemoteSession(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions";
        log.info("MVUMO: creating remote consent session");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getRemoteSession(String sessionId) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId;
        log.debug("MVUMO: get remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode verifyRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/verify";
        log.info("MVUMO: verify remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode grantRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/grant";
        log.info("MVUMO: grant remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode refuseRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/refuse";
        log.info("MVUMO: refuse remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode withdrawRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/withdraw";
        log.info("MVUMO: withdraw remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
