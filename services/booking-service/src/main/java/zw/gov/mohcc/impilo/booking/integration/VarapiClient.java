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
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiClient(RestTemplate restTemplate,
                        @Value("${booking.integrations.varapi-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProvider(TrustContext ctx, String providerPublicId) {
        String url = baseUrl + "/v1/internal/providers/" + providerPublicId;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return Map.of();
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Map<String, Object> out = new LinkedHashMap<>();
                dataMap.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
            return body;
        } catch (Exception ex) {
            log.warn("VARAPI getProvider failed for {}: {}", providerPublicId, ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkEligibility(TrustContext ctx, String providerPublicId, Long facilityId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("providerPublicId", providerPublicId);
        if (facilityId != null) {
            request.put("facilityId", facilityId);
        }
        String url = baseUrl + "/v1/internal/eligibility/check";
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return Map.of("eligible", false);
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Map<String, Object> out = new LinkedHashMap<>();
                dataMap.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
            return body;
        } catch (Exception ex) {
            log.warn("VARAPI checkEligibility failed: {}", ex.getMessage());
            return Map.of("eligible", false);
        }
    }
}
