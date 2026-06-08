package zw.gov.mohcc.impilo.booking.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PctClient {

    private static final Logger log = LoggerFactory.getLogger(PctClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PctClient(RestTemplate restTemplate,
                     @Value("${booking.integrations.pct-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> startJourney(TrustContext ctx, Map<String, Object> payload) {
        return post(ctx, "/v1/journeys/start", payload);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> enqueue(TrustContext ctx, UUID queueId, Map<String, Object> payload) {
        return post(ctx, "/v1/queues/" + queueId + "/enqueue", payload);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> startEncounter(TrustContext ctx, UUID journeyId, Map<String, Object> payload) {
        return post(ctx, "/v1/journeys/" + journeyId + "/encounter/start", payload);
    }

    private Map<String, Object> post(TrustContext ctx, String path, Map<String, Object> payload) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(payload != null ? payload : new LinkedHashMap<>(),
                            IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            return extractData(response.getBody());
        } catch (Exception ex) {
            log.warn("PCT POST {} failed: {}", path, ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractData(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> out = new LinkedHashMap<>();
            dataMap.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return body;
    }
}
