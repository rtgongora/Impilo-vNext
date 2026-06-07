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
public class FundoClient {

    private static final Logger log = LoggerFactory.getLogger(FundoClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FundoClient(RestTemplate restTemplate, @Value("${live.integrations.fundo-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    public Map<String, Object> getCourse(TrustContext ctx, String id) {
        return get(ctx, baseUrl + "/internal/v1/learning/v11/catalog/" + id);
    }

    public Map<String, Object> recordLiveCompletion(TrustContext ctx, Map<String, Object> body) {
        return post(ctx, baseUrl + "/internal/v1/learning/v11/sessions/live-completion", body);
    }

    public Map<String, Object> issueCertificate(TrustContext ctx, String enrolmentId) {
        return post(ctx, baseUrl + "/internal/v1/learning/v11/enrolments/" + enrolmentId + "/certificate", Map.of());
    }

    private Map<String, Object> get(TrustContext ctx, String url) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(IntegrationHttpSupport.trustHeaders(ctx)), new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception ex) {
            log.warn("FundoClient GET {} failed: {}", url, ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> post(TrustContext ctx, String url, Map<String, Object> body) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, IntegrationHttpSupport.trustHeaders(ctx)), new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception ex) {
            log.warn("FundoClient POST {} failed: {}", url, ex.getMessage());
            return Map.of();
        }
    }
}
