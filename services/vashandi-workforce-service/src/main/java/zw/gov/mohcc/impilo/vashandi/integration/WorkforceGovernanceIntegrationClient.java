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
public class WorkforceGovernanceIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(WorkforceGovernanceIntegrationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WorkforceGovernanceIntegrationClient(RestTemplate vashandiRestTemplate,
                                                @Value("${impilo.vashandi.integration.workforce-governance-base-url}") String baseUrl) {
        this.restTemplate = vashandiRestTemplate;
        this.baseUrl = baseUrl;
    }

    public IntegrationCheckResult fetchHscEmploymentSummary(String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return IntegrationCheckResult.degraded("workforce-governance", "health_id required");
        }
        try {
            String url = baseUrl + "/v1/internal/governance/hsc-employment/by-subject/" + healthId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            return IntegrationCheckResult.live("workforce-governance", response.getBody());
        } catch (RestClientException ex) {
            log.warn("Workforce governance unavailable for healthId {}: {}", healthId, ex.getMessage());
            return IntegrationCheckResult.degraded("workforce-governance", ex.getMessage());
        }
    }

    public Optional<Map<String, Object>> fetchOrganisation(UUID organisationId) {
        try {
            String url = baseUrl + "/v1/internal/governance/organisations/" + organisationId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Workforce governance organisation lookup failed: {}", ex.getMessage());
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
        } catch (IllegalStateException e) {
            log.debug("No trust context for workforce-governance call");
        }
        return headers;
    }
}
