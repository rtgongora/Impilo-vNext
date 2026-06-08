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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TusoClient {

    private static final Logger log = LoggerFactory.getLogger(TusoClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoClient(RestTemplate restTemplate,
                      @Value("${booking.integrations.tuso-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listResources(TrustContext ctx, Long facilityId, String resourceType) {
        String url = baseUrl + "/v1/facilities/" + facilityId + "/resources";
        if (resourceType != null && !resourceType.isBlank()) {
            url += "?resourceType=" + resourceType;
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            return extractList(response.getBody());
        } catch (Exception ex) {
            log.warn("TUSO listResources failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAvailableSlots(TrustContext ctx, Long facilityId, String date, String resourceType) {
        String url = baseUrl + "/v1/facilities/" + facilityId + "/calendars/slots?date=" + date;
        if (resourceType != null && !resourceType.isBlank()) {
            url += "&resourceType=" + resourceType;
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return List.of();
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> slotMap && slotMap.get("slots") instanceof List<?> slots) {
                return slots.stream()
                        .filter(Map.class::isInstance)
                        .map(s -> (Map<String, Object>) s)
                        .toList();
            }
            return extractList(body);
        } catch (Exception ex) {
            log.warn("TUSO getAvailableSlots failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createBooking(TrustContext ctx, UUID resourceId, Map<String, Object> payload) {
        String url = baseUrl + "/v1/resources/" + resourceId + "/bookings";
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(payload, IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            return extractData(response.getBody());
        } catch (Exception ex) {
            log.warn("TUSO createBooking failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getFacility(TrustContext ctx, Long facilityId) {
        String url = baseUrl + "/v1/facilities/" + facilityId;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            return extractData(response.getBody());
        } catch (Exception ex) {
            log.warn("TUSO getFacility failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractList(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
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
