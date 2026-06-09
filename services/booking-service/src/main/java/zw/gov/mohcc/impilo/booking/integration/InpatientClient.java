package zw.gov.mohcc.impilo.booking.integration;

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

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InpatientClient {

    private static final Logger log = LoggerFactory.getLogger(InpatientClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InpatientClient(RestTemplate restTemplate,
                           @Value("${booking.integrations.inpatient-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createProcedureEpisode(TrustContext ctx, Map<String, Object> payload) {
        return post(ctx, "/internal/v1/procedures/episodes", payload);
    }

    private Map<String, Object> post(TrustContext ctx, String path, Map<String, Object> payload) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(payload != null ? payload : new LinkedHashMap<>(),
                            IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            return extractBody(response.getBody());
        } catch (Exception ex) {
            log.warn("Inpatient POST {} failed: {}", path, ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractBody(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        return body;
    }
}
