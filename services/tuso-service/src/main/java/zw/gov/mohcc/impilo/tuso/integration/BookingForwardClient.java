package zw.gov.mohcc.impilo.tuso.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Deprecated compatibility shim — forwards legacy {@code /v1/appointments} callers to booking-service.
 */
@Component
@ConditionalOnProperty(name = "impilo.booking.forward-enabled", havingValue = "true", matchIfMissing = true)
public class BookingForwardClient {

    private static final Logger log = LoggerFactory.getLogger(BookingForwardClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BookingForwardClient(RestTemplate restTemplate,
                                @Value("${impilo.booking.base-url:http://localhost:8265}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public JsonNode listAppointments(String patientId, Long facilityId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments")
                .queryParam("page", page)
                .queryParam("size", size);
        if (patientId != null) builder.queryParam("patientId", patientId);
        if (facilityId != null) builder.queryParam("facilityId", facilityId);
        if (status != null) builder.queryParam("status", status);
        log.debug("TUSO shim: forward list appointments");
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    public JsonNode getAppointment(String id) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/appointments/" + id, JsonNode.class));
    }

    public JsonNode createAppointment(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/appointments", body, JsonNode.class));
    }

    public JsonNode confirmAppointment(String id) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/appointments/" + id + "/confirm", Map.of(), JsonNode.class));
    }

    public JsonNode cancelAppointment(String id, String reason) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/appointments/" + id + "/cancel",
                Map.of("reason", reason != null ? reason : "Cancelled"), JsonNode.class));
    }

    public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/appointments/citizen/" + cpid)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null) builder.queryParam("status", status);
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    public JsonNode createCitizenAppointment(String cpid, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/appointments/citizen/" + cpid, body, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
