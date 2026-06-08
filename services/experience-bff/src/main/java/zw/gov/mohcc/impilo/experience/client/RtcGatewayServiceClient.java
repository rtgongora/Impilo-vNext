package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

@Component
public class RtcGatewayServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayServiceClient.class);
    private static final String API = "/internal/v1/rtc";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RtcGatewayServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.rtcGatewayBaseUrl();
    }

    public JsonNode provisionSession(Map<String, Object> body) {
        log.info("RTC: provision session");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(baseUrl + API + "/sessions", new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getSession(String sessionId) {
        log.debug("RTC: get session id={}", sessionId);
        return extractData(restTemplate.getForEntity(baseUrl + API + "/sessions/" + sessionId, JsonNode.class));
    }

    public JsonNode issueParticipantToken(String sessionId, Map<String, Object> body) {
        log.info("RTC: issue token sessionId={}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + API + "/sessions/" + sessionId + "/participants/token",
                new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode endSession(String sessionId) {
        log.info("RTC: end session id={}", sessionId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(baseUrl + API + "/sessions/" + sessionId + "/end", new HttpEntity<>(Map.of()), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getOpsHealth() {
        log.debug("RTC: ops health");
        return extractData(restTemplate.getForEntity(baseUrl + API + "/ops/health", JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
