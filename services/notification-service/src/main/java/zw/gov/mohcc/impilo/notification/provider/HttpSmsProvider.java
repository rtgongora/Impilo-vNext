package zw.gov.mohcc.impilo.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-based SMS notification provider.
 * <p>
 * Sends SMS messages via a configurable HTTP SMS gateway API.
 * Compatible with common SMS gateway patterns (e.g. Africa's Talking, Twilio-style APIs).
 * Activated when {@code notification.sms.provider=http} is set.
 */
@Component
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "http", matchIfMissing = false)
public class HttpSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsProvider.class);

    private final String gatewayUrl;
    private final String apiKey;
    private final String senderId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpSmsProvider(
            @Value("${notification.sms.gateway.url}") String gatewayUrl,
            @Value("${notification.sms.gateway.api-key:}") String apiKey,
            @Value("${notification.sms.gateway.sender-id:IMPILO}") String senderId,
            ObjectMapper objectMapper) {
        this.gatewayUrl = gatewayUrl;
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("HTTP SMS provider initialised: gateway={}, sender={}", gatewayUrl, senderId);
    }

    @Override
    public String name() {
        return "http_sms";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("to", to);
            payload.put("from", senderId);
            payload.put("message", body != null ? body : "");
            if (subject != null && !subject.isBlank()) {
                payload.put("subject", subject);
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("SMS sent successfully: to={}, status={}", to, response.statusCode());
            } else {
                log.error("SMS gateway returned error: to={}, status={}, body={}",
                        to, response.statusCode(), response.body());
                throw new NotificationDeliveryException("SMS", to,
                        new RuntimeException("Gateway returned " + response.statusCode()));
            }
        } catch (NotificationDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send SMS: to={}, error={}", to, e.getMessage());
            throw new NotificationDeliveryException("SMS", to, e);
        }
    }
}
