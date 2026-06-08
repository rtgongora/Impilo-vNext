package zw.gov.mohcc.impilo.madi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class ButanoIntegration {

    private static final Logger log = LoggerFactory.getLogger(ButanoIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ButanoIntegration(RestTemplate restTemplate,
                             @Value("${madi.integration.butano.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void recordObservation(String episodeId, String observationType,
                                  BigDecimal valueNumeric, String valueText) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("episodeId", episodeId);
        payload.put("observationType", observationType);
        if (valueNumeric != null) {
            payload.put("valueNumeric", valueNumeric);
        }
        if (valueText != null) {
            payload.put("valueText", valueText);
        }
        post("/internal/v1/fhir/Observation", payload);
    }

    public void recordTransfusionVerified(String patientCpid, String episodeId) {
        post("/internal/v1/fhir/Procedure", Map.of(
                "patientCpid", patientCpid,
                "episodeId", episodeId,
                "status", "completed",
                "source", "madi-service"));
    }

    private void post(String path, Map<String, Object> payload) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(baseUrl + path, new HttpEntity<>(payload, headers), Void.class);
        } catch (RestClientException e) {
            log.warn("BUTANO unavailable for {}: {}", path, e.getMessage());
        }
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        } catch (IllegalStateException ignored) {
            // graceful in dev
        }
        return headers;
    }
}
