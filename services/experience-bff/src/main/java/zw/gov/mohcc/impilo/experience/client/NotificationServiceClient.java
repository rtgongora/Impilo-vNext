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
        String url = baseUrl + "/internal/v1/notify";
        log.info("Notification: send");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    /**
     * Send with explicit override headers — for service-originated (pre-auth) flows such
     * as contact-OTP delivery, where the inbound request is anonymous so there is no
     * Authorization to forward, while notification-service's {@code /internal} lane is
     * fail-closed JWT-authenticated (rig-caught W1 defect: the R1 OTP lane 503'd because
     * this hop arrived unauthenticated). The shared trust-header interceptor still
     * overwrites these values with the inbound ones when the caller IS authenticated.
     */
    public JsonNode sendNotification(Map<String, Object> body, org.springframework.http.HttpHeaders overrideHeaders) {
        String url = baseUrl + "/internal/v1/notify";
        log.info("Notification: send (service-originated)");
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body, overrideHeaders), JsonNode.class));
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

    public JsonNode markAllAsRead() {
        String url = baseUrl + "/internal/v1/notifications/read-all";
        log.info("Notification: markAllAsRead");
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode unreadCount() {
        return unreadCount(null);
    }

    public JsonNode unreadCount(String recipientId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/notifications/inbox/unread-count");
        if (recipientId != null && !recipientId.isBlank()) {
            builder.queryParam("recipientId", recipientId);
        }
        String url = builder.toUriString();
        log.debug("Notification: unreadCount recipientId={}", recipientId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getPreferences() {
        String url = baseUrl + "/internal/v1/notifications/preferences";
        log.debug("Notification: getPreferences");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getPreferences(String patientId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/notifications/preferences")
                .queryParam("patientId", patientId)
                .toUriString();
        log.debug("Notification: getPreferences patientId={}", patientId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode updatePreferences(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/notifications/preferences";
        log.info("Notification: updatePreferences");
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updatePreferences(String patientId, Map<String, Object> body) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/notifications/preferences")
                .queryParam("patientId", patientId)
                .toUriString();
        log.info("Notification: updatePreferences patientId={}", patientId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode channelStatus() {
        String url = baseUrl + "/internal/v1/channels/status";
        log.debug("Notification: channelStatus");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listTemplates() {
        String url = baseUrl + "/internal/v1/templates";
        log.debug("Notification: listTemplates");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getTemplateByKey(String key) {
        String url = baseUrl + "/internal/v1/templates/" + key;
        log.debug("Notification: getTemplateByKey key={}", key);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createTemplate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/templates";
        log.info("Notification: createTemplate");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode publishTemplate(String templateId) {
        String url = baseUrl + "/internal/v1/templates/" + templateId + "/publish";
        log.info("Notification: publishTemplate id={}", templateId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode retireTemplate(String templateId) {
        String url = baseUrl + "/internal/v1/templates/" + templateId + "/retire";
        log.info("Notification: retireTemplate id={}", templateId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode listTemplateVersions(String templateId) {
        String url = baseUrl + "/internal/v1/templates/" + templateId + "/versions";
        log.debug("Notification: listTemplateVersions id={}", templateId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createTemplateVersion(String templateId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/templates/" + templateId + "/versions";
        log.info("Notification: createTemplateVersion id={}", templateId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}

