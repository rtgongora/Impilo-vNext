package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for notification-service.
 */
@Component
public class NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NotificationServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.notificationBaseUrl();
    }

    public JsonNode sendNotification(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/notifications/send";
        log.info("Notification: send");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listNotifications(String recipientId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/notifications");
        if (recipientId != null && !recipientId.isBlank()) {
            b.queryParam("recipientId", recipientId);
        }
        String url = b.toUriString();
        log.debug("Notification: list [recipientId={}]", recipientId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listNotifications(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/notifications")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.debug("Notification: listNotifications");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode markAsRead(String id) {
        String url = baseUrl + "/internal/v1/notifications/" + id + "/read";
        log.info("Notification: markAsRead id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PATCH, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getPreferences() {
        String url = baseUrl + "/internal/v1/notifications/preferences";
        log.debug("Notification: getPreferences");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode updatePreferences(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/notifications/preferences";
        log.info("Notification: updatePreferences");
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}

