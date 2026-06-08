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

import java.util.HashMap;
import java.util.Map;

@Service
public class OrosIntegration {

    private static final Logger log = LoggerFactory.getLogger(OrosIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosIntegration(RestTemplate restTemplate,
                           @Value("${madi.integration.oros.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void notifyOrderSubmitted(String orosOrderRef, String madiOrderId) {
        post("/internal/v1/orders/blood/submitted", Map.of(
                "orosOrderRef", orosOrderRef,
                "madiOrderId", madiOrderId,
                "source", "madi-service"));
    }

    public void notifyBloodIssued(String orosOrderRef, String unitId) {
        post("/internal/v1/orders/blood/issued", Map.of(
                "orosOrderRef", orosOrderRef,
                "unitId", unitId,
                "source", "madi-service"));
    }

    private void post(String path, Map<String, Object> payload) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(baseUrl + path, new HttpEntity<>(payload, headers), Void.class);
        } catch (RestClientException e) {
            log.warn("OROS unavailable for {}: {}", path, e.getMessage());
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
            log.debug("No trust context for OROS call");
        }
        return headers;
    }
}
