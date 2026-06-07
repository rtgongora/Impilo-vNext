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

import java.util.Map;

@Service
public class NhumeIntegration {

    private static final Logger log = LoggerFactory.getLogger(NhumeIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NhumeIntegration(RestTemplate restTemplate,
                            @Value("${madi.integration.nhume.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void notifyAdverseEvent(String reactionId, String reactionType, String severity) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "reactionId", reactionId,
                    "reactionType", reactionType,
                    "severity", severity,
                    "source", "madi-service");
            restTemplate.postForEntity(baseUrl + "/internal/v1/reporting/adverse-events",
                    new HttpEntity<>(payload, headers), Void.class);
        } catch (RestClientException e) {
            log.warn("NHUME reporting unavailable for reaction {}: {}", reactionId, e.getMessage());
        }
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
