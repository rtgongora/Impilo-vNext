package zw.gov.mohcc.impilo.madi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class NdilaIntegration {

    private static final Logger log = LoggerFactory.getLogger(NdilaIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NdilaIntegration(RestTemplate restTemplate,
                            @Value("${madi.integration.ndila.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findDrivesNearSite(String ndilaSiteRef, double radiusKm) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            String url = baseUrl + "/internal/v1/sites/" + ndilaSiteRef + "/nearby-drives?radiusKm=" + radiusKm;
            var response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getBody() != null && response.getBody().get("drives") instanceof List<?> drives) {
                return (List<Map<String, Object>>) drives;
            }
        } catch (RestClientException e) {
            log.warn("NDILA unavailable for site {}: {}", ndilaSiteRef, e.getMessage());
        }
        return Collections.emptyList();
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        } catch (IllegalStateException ignored) {
            // graceful in dev
        }
        return headers;
    }
}
