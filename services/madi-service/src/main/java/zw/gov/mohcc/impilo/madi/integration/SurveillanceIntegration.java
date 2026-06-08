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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pulls epidemiology counters/alerts to tune blood-demand forecasts.
 */
@Service
public class SurveillanceIntegration {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceIntegration.class);

    /** Syndrome codes that elevate blood-product demand in forecast tuning. */
    private static final Set<String> BLOOD_DEMAND_SYNDROMES = Set.of(
            "HEMORRHAGE", "MASS_CASUALTY", "TRAUMA_HEMORRHAGE",
            "MALARIA", "DENGUE", "CHOLERA", "OBSTETRIC_HEMORRHAGE"
    );

    private static final Map<String, Double> SYNDROME_WEIGHTS = Map.of(
            "HEMORRHAGE", 0.25,
            "MASS_CASUALTY", 0.40,
            "TRAUMA_HEMORRHAGE", 0.30,
            "OBSTETRIC_HEMORRHAGE", 0.20,
            "MALARIA", 0.10,
            "DENGUE", 0.08,
            "CHOLERA", 0.05
    );

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SurveillanceIntegration(RestTemplate restTemplate,
                                   @Value("${madi.integration.surveillance.base-url:http://localhost:8180}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public EpidemiologyTuning fetchBloodDemandTuning(UUID facilityId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        List<Map<String, Object>> signals = new ArrayList<>();
        double multiplier = 1.0;

        try {
            String counterUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/surveillance/counters")
                    .queryParam("from", from.toString())
                    .queryParam("to", to.toString())
                    .toUriString();
            HttpHeaders headers = buildTrustHeaders();
            ResponseEntity<Map<String, Object>> counterResponse = restTemplate.exchange(
                    counterUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            multiplier += applyCounters(counterResponse.getBody(), facilityId, signals);

            String alertUrl = baseUrl + "/internal/v1/surveillance/alerts?unacknowledgedOnly=true";
            ResponseEntity<Map<String, Object>> alertResponse = restTemplate.exchange(
                    alertUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            multiplier += applyAlerts(alertResponse.getBody(), facilityId, signals);
        } catch (RestClientException e) {
            log.warn("Surveillance epidemiology tuning unavailable: {}", e.getMessage());
            signals.add(Map.of(
                    "source", "surveillance",
                    "status", "UNAVAILABLE",
                    "message", "Forecast uses baseline demand only"));
        }

        multiplier = Math.min(2.5, Math.max(1.0, multiplier));
        return new EpidemiologyTuning(round(multiplier), List.copyOf(signals));
    }

    private double applyCounters(Map<String, Object> body, UUID facilityId, List<Map<String, Object>> signals) {
        if (body == null || !(body.get("counters") instanceof List<?> counters)) {
            return 0.0;
        }
        double boost = 0.0;
        for (Object item : counters) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> counter = (Map<String, Object>) raw;
            String syndrome = stringValue(counter.get("syndrome_code"));
            if (syndrome == null || !BLOOD_DEMAND_SYNDROMES.contains(syndrome)) {
                continue;
            }
            if (facilityId != null) {
                String counterFacility = stringValue(counter.get("facility_id"));
                if (counterFacility != null && !facilityId.toString().equals(counterFacility)) {
                    continue;
                }
            }
            int eventCount = parseInt(counter.get("event_count"), 0);
            if (eventCount <= 0) {
                continue;
            }
            double weight = SYNDROME_WEIGHTS.getOrDefault(syndrome, 0.05);
            double contribution = weight * Math.min(1.0, eventCount / 20.0);
            boost += contribution;
            signals.add(Map.of(
                    "source", "counter",
                    "syndromeCode", syndrome,
                    "eventCount", eventCount,
                    "contribution", round(contribution)));
        }
        return boost;
    }

    private double applyAlerts(Map<String, Object> body, UUID facilityId, List<Map<String, Object>> signals) {
        if (body == null || !(body.get("alerts") instanceof List<?> alerts)) {
            return 0.0;
        }
        double boost = 0.0;
        for (Object item : alerts) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> alert = (Map<String, Object>) raw;
            String syndrome = stringValue(alert.get("syndrome_code"));
            if (syndrome == null || !BLOOD_DEMAND_SYNDROMES.contains(syndrome)) {
                continue;
            }
            if (facilityId != null) {
                String alertFacility = stringValue(alert.get("facility_id"));
                if (alertFacility != null && !facilityId.toString().equals(alertFacility)) {
                    continue;
                }
            }
            double weight = SYNDROME_WEIGHTS.getOrDefault(syndrome, 0.08);
            boost += weight;
            signals.add(new LinkedHashMap<>(Map.of(
                    "source", "alert",
                    "syndromeCode", syndrome,
                    "triggerCount", parseInt(alert.get("trigger_count"), 0),
                    "threshold", parseInt(alert.get("threshold"), 0),
                    "contribution", round(weight))));
        }
        return boost;
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.actorId() != null) {
                headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            }
        } catch (IllegalStateException ignored) {
            // dev fallback
        }
        return headers;
    }

    private static String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record EpidemiologyTuning(double multiplier, List<Map<String, Object>> signals) {
    }
}
