package zw.gov.mohcc.impilo.learning.fundo.notify;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Real delivery provider that forwards intents to the platform comms hub
 * (notification-service {@code POST /internal/v1/notify}) — the same endpoint the
 * Experience BFF already uses. Consume-not-rebuild: this never owns comms logic.
 *
 * <p>Selected only when {@code learning.notifications.dispatch.provider=NOTIFICATION_SERVICE}.
 * If no base URL is configured it degrades honestly to {@code RECORDED} (acknowledged,
 * not delivered) rather than silently dropping or faking the message.
 */
@Component
public class NotificationServiceLearningProvider implements LearningNotificationProvider {

    public static final String KEY = "NOTIFICATION_SERVICE";
    private static final Logger log = LoggerFactory.getLogger(NotificationServiceLearningProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public NotificationServiceLearningProvider(
            @Value("${learning.notifications.dispatch.notification-base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public DeliveryOutcome deliver(Intent intent) {
        if (baseUrl.isBlank()) {
            return DeliveryOutcome.recorded(
                    "notification-service base URL not configured: intent recorded, not delivered");
        }
        try {
            String url = trimSlash(baseUrl) + "/internal/v1/notify";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("channel", intent.channelPreference() == null ? "IN_APP" : intent.channelPreference());
            body.put("title", intent.title());
            body.put("message", intent.message());
            body.put("recipientId", intent.subjectId());
            body.put("eventCode", intent.eventCode());
            body.put("metadata", intent.metadata() == null ? Map.of() : intent.metadata());

            HttpHeaders headers = new HttpHeaders();
            if (intent.tenantId() != null) {
                headers.set("X-Tenant-ID", intent.tenantId().toString());
            }
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return DeliveryOutcome.sent("delivered via notification-service");
        } catch (Exception ex) {
            log.warn("notification-service delivery failed for intent {}: {}", intent.id(), ex.getMessage());
            return DeliveryOutcome.failed(ex.getMessage() == null ? "delivery failed" : ex.getMessage());
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
