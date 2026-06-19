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

@Component
public class VarapiIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiIntegrationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiIntegrationClient(RestTemplate vashandiRestTemplate,
                                   @Value("${impilo.vashandi.integration.varapi-base-url}") String baseUrl) {
        this.restTemplate = vashandiRestTemplate;
        this.baseUrl = baseUrl;
    }

    public IntegrationCheckResult fetchProfessionalStatus(String providerWorkerId) {
        if (providerWorkerId == null || providerWorkerId.isBlank()) {
            return IntegrationCheckResult.degraded("varapi", "provider_worker_id required");
        }
        try {
            String url = baseUrl + "/v1/internal/providers/" + providerWorkerId + "/status-summary";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            return IntegrationCheckResult.live("varapi", response.getBody());
        } catch (RestClientException ex) {
            log.warn("VARAPI unavailable for provider {}: {}", providerWorkerId, ex.getMessage());
            return IntegrationCheckResult.degraded("varapi", ex.getMessage());
        }
    }

    public Optional<Map<String, Object>> findProvider(String providerWorkerId) {
        IntegrationCheckResult result = fetchProfessionalStatus(providerWorkerId);
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
            log.debug("No trust context for VARAPI call");
        }
        return headers;
    }
}
