package zw.gov.mohcc.impilo.daidzai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the facilities whose catchment an emergency falls into, via the
 * governed Ndila spatial index. Facilities are surveillance nodes: an event in
 * their vicinity must reach them. Degraded-safe — a dead location service
 * never blocks incident creation; the incident simply carries no catchment set.
 */
@Component
public class NdilaCatchmentClient {

    private static final Logger log = LoggerFactory.getLogger(NdilaCatchmentClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public NdilaCatchmentClient(
            @Value("${daidzai.ndila.base-url:http://ndila-service:8155}") String baseUrl,
            @Value("${daidzai.ndila.timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record CatchmentFacility(String locationId, String name, double distanceMeters) {}

    public List<CatchmentFacility> nearestFacilities(UUID tenantId, double lat, double lng, int limit) {
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/api/v1/ndila/spatial/nearby")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.set("X-Tenant-ID", tenantId.toString());
                        headers.set("X-Pod-ID", "national-spine");
                        headers.set("X-Request-ID", UUID.randomUUID().toString());
                        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
                        // Propagate the escalating actor's bearer so Ndila authorizes honestly.
                        String bearer = currentBearer();
                        if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer);
                    })
                    .body(Map.of(
                            "origin", Map.of("latitude", lat, "longitude", lng),
                            "radiusMeters", 50_000,
                            "entityTypes", List.of("FACILITY", "CLINIC", "HOSPITAL"),
                            "limit", limit))
                    .retrieve()
                    .body(JsonNode.class);
            List<CatchmentFacility> out = new ArrayList<>();
            JsonNode matches = response == null ? null : response.path("matches");
            if (matches != null && matches.isArray()) {
                for (JsonNode m : matches) {
                    out.add(new CatchmentFacility(
                            m.path("id").asText(null),
                            m.path("name").asText(null),
                            m.path("distanceMeters").asDouble(0)));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("Catchment resolution unavailable ({}, {}): {}", lat, lng, e.toString());
            return List.of();
        }
    }

    private static String currentBearer() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                return request.getHeader(HttpHeaders.AUTHORIZATION);
            }
        } catch (Exception ignored) {
            // Outside a request — proceed without a bearer.
        }
        return null;
    }
}
