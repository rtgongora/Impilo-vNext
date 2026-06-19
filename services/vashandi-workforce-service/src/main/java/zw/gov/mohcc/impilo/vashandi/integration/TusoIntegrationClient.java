package zw.gov.mohcc.impilo.vashandi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class TusoIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(TusoIntegrationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoIntegrationClient(RestTemplate vashandiRestTemplate,
                                 @Value("${impilo.vashandi.integration.tuso-base-url}") String baseUrl) {
        this.restTemplate = vashandiRestTemplate;
        this.baseUrl = baseUrl;
    }

    public IntegrationCheckResult validateFacility(UUID facilityId) {
        if (facilityId == null) {
            return IntegrationCheckResult.degraded("tuso", "facility_id required");
        }
        try {
            String url = baseUrl + "/v1/internal/facilities/" + facilityId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            return IntegrationCheckResult.live("tuso", response.getBody());
        } catch (RestClientException ex) {
            log.warn("TUSO unavailable for facility {}: {}", facilityId, ex.getMessage());
            return IntegrationCheckResult.degraded("tuso", ex.getMessage());
        }
    }

    public Optional<Map<String, Object>> getFacility(UUID facilityId) {
        IntegrationCheckResult result = validateFacility(facilityId);
        return result.payload();
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
            log.debug("No trust context for TUSO call");
        }
        return headers;
    }
}
