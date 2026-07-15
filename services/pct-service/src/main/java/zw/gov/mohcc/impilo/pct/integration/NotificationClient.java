package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thin client to notification-service ({@code POST /internal/v1/notify}) for real per-recipient
 * delivery intents. Trauma-team activation and escalation create one {@code ns_notifications} row
 * per rostered member — a genuine delivery-intent (status PENDING), not a fabricated "sent". Returns
 * the notification id so PCT can record the delivery ref on the member; null if notification-service
 * is unreachable (an activation is never blocked by a degraded notifier — the null ref is honest).
 */
@Service
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NotificationClient(
            RestTemplate restTemplate,
            @Value("${pct.integration.notification.base-url:http://localhost:8200}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Enqueue a notification. Returns the created notification id (delivery-intent), or null on failure.
     *
     * @param templateKey registered ns_templates key (e.g. {@code ED_TRAUMA_TEAM})
     * @param channel     delivery channel (e.g. {@code PUSH})
     * @param recipient   the recipient address / workforce profile id
     */
    @SuppressWarnings("unchecked")
    public String notify(UUID tenantId, String templateKey, String channel, String recipient,
                         Map<String, String> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateKey", templateKey);
        body.put("channel", channel);
        body.put("to", recipient);
        body.put("variables", variables != null ? variables : Map.of());
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/notify",
                    new HttpEntity<>(body, headers(tenantId)), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object id = resp.getBody().getOrDefault("id", resp.getBody().get("notificationId"));
                return id != null ? id.toString() : null;
            }
            log.warn("notification-service /notify returned {} for template {}", resp.getStatusCode(), templateKey);
        } catch (RestClientException e) {
            log.warn("notification-service unreachable ({}): {} — delivery ref stays null (honest)",
                    templateKey, e.getMessage());
        }
        return null;
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "pct-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        h.set("Idempotency-Key", "pct-notify-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
