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
import java.util.UUID;

/**
 * HTTP client for channels-service.
 *
 * <p>This client is a thin proxy wrapper around internal v1 routes.</p>
 */
@Component
public class ChannelsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ChannelsServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ChannelsServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.channelsBaseUrl();
    }

    // Channel registry (configuration)
    public JsonNode listChannels() {
        String url = baseUrl + "/internal/v1/channels";
        log.debug("Channels: list");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getChannel(String id) {
        String url = baseUrl + "/internal/v1/channels/" + id;
        log.debug("Channels: get id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createChannel(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/channels";
        log.info("Channels: create");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode updateChannel(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/channels/" + id;
        log.info("Channels: update id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode testChannel(String id) {
        String url = baseUrl + "/internal/v1/channels/" + id + "/test";
        log.info("Channels: test id={}", id);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    // Messaging sessions + messages (runtime)
    public JsonNode listSessions() {
        String url = baseUrl + "/internal/v1/channels/sessions";
        log.debug("Channels: listSessions");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getSession(UUID id) {
        String url = baseUrl + "/internal/v1/channels/sessions/" + id;
        log.debug("Channels: getSession {}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createSession(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/channels/sessions";
        log.info("Channels: createSession");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode sendOutboundMessage(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/channels/messages";
        log.info("Channels: sendOutboundMessage");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listMessagesForSession(UUID sessionId) {
        String url = baseUrl + "/internal/v1/channels/messages/sessions/" + sessionId;
        log.debug("Channels: listMessagesForSession {}", sessionId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}

