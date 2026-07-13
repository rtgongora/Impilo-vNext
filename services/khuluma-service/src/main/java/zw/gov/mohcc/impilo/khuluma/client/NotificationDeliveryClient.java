package zw.gov.mohcc.impilo.khuluma.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for notification-service (port 8200) — the platform's delivery/providers system-of-record
 * (SMS / WhatsApp / EMAIL / USSD). Per the registry, khuluma must <b>delegate</b> external delivery
 * here rather than owning provider adapters (G31). The shared {@code serviceRestTemplate} propagates
 * the v1.1 companion trust headers (synthesizing them on service-side threads), so downstream authz +
 * Mvumo communication-preference gating see the caller. Best-effort: a down notification-service
 * degrades honestly (never reports a send that did not happen).
 */
@Component
public class NotificationDeliveryClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryClient.class);
    private static final String NOTIFY = "/internal/v1/notify";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NotificationDeliveryClient(RestTemplate serviceRestTemplate,
                                      @Value("${impilo.khuluma.notification-base-url:http://localhost:8200}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Outcome of a delegated delivery. {@code delivered} means notification-service accepted the send. */
    public record DeliveryResult(boolean delivered, String reference, String error) {
        static DeliveryResult ok(String reference) { return new DeliveryResult(true, reference, null); }
        static DeliveryResult failed(String error) { return new DeliveryResult(false, null, error); }
    }

    /**
     * Hand an external-channel delivery to notification-service. The request is template-driven
     * ({@code templateKey} + {@code variables}); notification-service resolves the provider, renders,
     * and applies communication-preference gating.
     */
    public DeliveryResult deliver(String channel, String recipient, String templateKey,
                                  Map<String, String> variables, String patientRef, String messageKind) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("templateKey", templateKey);
        req.put("channel", channel);
        req.put("to", recipient);
        req.put("variables", variables != null ? variables : Map.of());
        if (patientRef != null && !patientRef.isBlank()) req.put("patientRef", patientRef);
        if (messageKind != null && !messageKind.isBlank()) req.put("messageKind", messageKind);
        try {
            RequestEntity<Object> entity = RequestEntity
                    .post(URI.create(baseUrl + NOTIFY))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req);
            ResponseEntity<JsonNode> response = restTemplate.exchange(entity, JsonNode.class);
            JsonNode payload = response.getBody();
            JsonNode data = payload != null && payload.has("data") && !payload.get("data").isNull()
                    ? payload.get("data") : payload;
            String ref = data != null && data.hasNonNull("id") ? data.get("id").asText() : null;
            return DeliveryResult.ok(ref);
        } catch (RestClientException ex) {
            log.warn("notification-service delivery ({} → {}) failed: {}", channel, recipient, ex.getMessage());
            return DeliveryResult.failed(ex.getMessage());
        }
    }
}
