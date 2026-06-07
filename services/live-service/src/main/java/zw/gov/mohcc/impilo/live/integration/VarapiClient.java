package zw.gov.mohcc.impilo.live.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;

@Component
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiClient(RestTemplate restTemplate, @Value("${live.integrations.varapi-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    public Map<String, Object> getProvider(TrustContext ctx, String providerId) {
        return get(ctx, baseUrl + "/v1/internal/providers/" + providerId);
    }

    public Map<String, Object> checkEligibility(TrustContext ctx, Map<String, Object> request) {
        return post(ctx, baseUrl + "/v1/internal/eligibility/check", request);
    }

    private Map<String, Object> get(TrustContext ctx, String url) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(IntegrationHttpSupport.trustHeaders(ctx)), new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception ex) {
            log.warn("VarapiClient GET {} failed: {}", url, ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> post(TrustContext ctx, String url, Map<String, Object> body) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, IntegrationHttpSupport.trustHeaders(ctx)), new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception ex) {
            log.warn("VarapiClient POST {} failed: {}", url, ex.getMessage());
            return Map.of();
        }
    }
}
