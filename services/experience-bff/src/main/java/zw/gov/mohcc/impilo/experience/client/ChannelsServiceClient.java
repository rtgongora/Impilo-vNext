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
 * HTTP client for the channels sovereign service (port 8301).
 * Manages configured communication channels.
 */
@Component
public class ChannelsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ChannelsServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ChannelsServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.channelsBaseUrl();
    }

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
        log.info("Channels: create [name={}]", body.get("name"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode updateChannel(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/channels/" + id;
        log.info("Channels: update id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode testChannel(String id) {
        String url = baseUrl + "/internal/v1/channels/" + id + "/test";
        log.info("Channels: test id={}", id);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
