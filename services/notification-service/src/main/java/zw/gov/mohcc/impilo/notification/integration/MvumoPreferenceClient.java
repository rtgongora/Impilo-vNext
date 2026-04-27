package zw.gov.mohcc.impilo.notification.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calls mvumo-service {@code POST /internal/v1/mvumo/communication-preferences/evaluate} for
 * pre-send and worker-time enforcement. Fails open if Mvumo is down.
 */
@Component
public class MvumoPreferenceClient {

    private static final Logger log = LoggerFactory.getLogger(MvumoPreferenceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean enabled;

    public MvumoPreferenceClient(
            @Qualifier("mvumoRestTemplate") RestTemplate mvumoRestTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.mvumo.base-url:http://localhost:8195}") String baseUrl,
            @Value("${impilo.mvumo.enabled:true}") boolean enabled) {
        this.restTemplate = mvumoRestTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.enabled = enabled;
    }

    /**
     * @return empty if gate skipped (not configured) or on transport error (fail-open); true/false
     *     for allowed
     */
    public Optional<Boolean> evaluateAllowed(String tenantId, String patientRef, String channel, String messageKind) {
        if (!enabled) {
            return Optional.empty();
        }
        if (patientRef == null || patientRef.isBlank()) {
            return Optional.empty();
        }
        String url = baseUrl + "/internal/v1/mvumo/communication-preferences/evaluate";
        Map<String, Object> body = new HashMap<>();
        body.put("patientRef", patientRef);
        body.put("proposedChannel", channel);
        if (messageKind != null) {
            body.put("messageKind", messageKind);
        }
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null && !tenantId.isBlank()) {
            headers.set("X-Tenant-ID", tenantId);
        }
        try {
            String raw =
                    restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.get("data");
            if (data == null || !data.isObject()) {
                return Optional.empty();
            }
            if (data.has("allowed")) {
                return Optional.of(data.get("allowed").asBoolean());
            }
        } catch (RestClientException e) {
            log.warn("Mvumo evaluate unavailable (allow send): {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Mvumo evaluate parse error (allow send): {}", e.getMessage());
        }
        return Optional.empty();
    }
}
