package zw.gov.mohcc.impilo.madi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class IotIntegration {

    private static final Logger log = LoggerFactory.getLogger(IotIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IotIntegration(RestTemplate restTemplate,
                          @Value("${madi.integration.iot.base-url:http://localhost:8211}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Optional<IotTelemetryReading> fetchLatestTemperatureReading(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/telemetry/readings")
                .queryParam("device_id", deviceId)
                .queryParam("metric_type", "TEMPERATURE")
                .queryParam("limit", 1)
                .toUriString();
        try {
            HttpHeaders headers = buildTrustHeaders();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null || !(body.get("items") instanceof List<?> items) || items.isEmpty()) {
                return Optional.empty();
            }
            Object first = items.getFirst();
            if (!(first instanceof Map<?, ?> raw)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) raw;
            BigDecimal value = parseDecimal(item.get("metric_value"), item.get("metricValue"));
            OffsetDateTime recordedAt = parseTimestamp(item.get("recorded_at"), item.get("recordedAt"));
            String readingRef = stringValue(item.get("reading_id"), item.get("readingId"));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(new IotTelemetryReading(readingRef, value, recordedAt != null ? recordedAt : OffsetDateTime.now()));
        } catch (RestClientException e) {
            log.warn("IoT telemetry unavailable for device {}: {}", deviceId, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context for IoT call");
        }
        return headers;
    }

    private static BigDecimal parseDecimal(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            try {
                return new BigDecimal(candidate.toString());
            } catch (NumberFormatException ignored) {
                // try next
            }
        }
        return null;
    }

    private static OffsetDateTime parseTimestamp(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            try {
                return OffsetDateTime.parse(candidate.toString());
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private static String stringValue(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null && !candidate.toString().isBlank()) {
                return candidate.toString();
            }
        }
        return null;
    }

    public record IotTelemetryReading(String readingRef, BigDecimal temperatureC, OffsetDateTime recordedAt) {
    }
}
