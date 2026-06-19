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
public class FundoIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(FundoIntegrationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FundoIntegrationClient(RestTemplate vashandiRestTemplate,
                                  @Value("${impilo.vashandi.integration.fundo-base-url}") String baseUrl) {
        this.restTemplate = vashandiRestTemplate;
        this.baseUrl = baseUrl;
    }

    public IntegrationCheckResult fetchTrainingEvidence(String providerWorkerId) {
        if (providerWorkerId == null || providerWorkerId.isBlank()) {
            return IntegrationCheckResult.degraded("fundo", "provider_worker_id required");
        }
        try {
            String url = baseUrl + "/v1/internal/fundo/learners/" + providerWorkerId + "/cpd-summary";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            return IntegrationCheckResult.live("fundo", response.getBody());
        } catch (RestClientException ex) {
            log.warn("Fundo unavailable for provider {}: {}", providerWorkerId, ex.getMessage());
            return IntegrationCheckResult.degraded("fundo", ex.getMessage());
        }
    }

    public Optional<Map<String, Object>> getCpdSummary(String providerWorkerId) {
        return fetchTrainingEvidence(providerWorkerId).payload();
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        } catch (IllegalStateException e) {
            log.debug("No trust context for Fundo call");
        }
        return headers;
    }
}
