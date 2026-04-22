package zw.gov.mohcc.impilo.tshepo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.core.TrustHeaders;

import java.util.UUID;

/**
 * Calls Workforce Governance assignment scope evaluation (facility context).
 */
@Service
public class GovernanceScopeClient {

    private static final Logger log = LoggerFactory.getLogger(GovernanceScopeClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String governanceBaseUrl;

    public GovernanceScopeClient(
            @Qualifier("governanceRestTemplate") RestTemplate governanceRestTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.services.governance.base-url:}") String governanceBaseUrl) {
        this.restTemplate = governanceRestTemplate;
        this.objectMapper = objectMapper;
        this.governanceBaseUrl = governanceBaseUrl;
    }

    public boolean isEnabled() {
        return governanceBaseUrl != null && !governanceBaseUrl.isBlank();
    }

    /**
     * @return true if allowed or governance disabled/unreachable (degraded allow), false if explicitly denied.
     */
    public boolean evaluateFacilityScope(UUID tenantId,
                                         String actorHealthId,
                                         String providerPublicId,
                                         Long tusoFacilityId,
                                         String correlationId) {
        if (!isEnabled()) {
            return true;
        }
        try {
            String url = trimSlash(governanceBaseUrl) + "/v1/internal/governance/scope/evaluate-facility";
            var node = objectMapper.createObjectNode();
            node.put("actorHealthId", actorHealthId);
            node.put("tusoFacilityId", tusoFacilityId);
            if (providerPublicId != null && !providerPublicId.isBlank()) {
                node.put("providerPublicId", providerPublicId);
            }
            String body = node.toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (tenantId != null) {
                headers.set(TrustHeaders.TENANT_ID, tenantId.toString());
            }
            if (actorHealthId != null) {
                headers.set(TrustHeaders.ACTOR_ID, actorHealthId);
            }
            headers.set(TrustHeaders.ACTOR_TYPE, "SYSTEM");
            headers.set(TrustHeaders.PURPOSE_OF_USE, "POLICY_EVALUATION");
            headers.set(TrustHeaders.DEVICE_FINGERPRINT, "tshepo-pdp");
            if (correlationId != null) {
                headers.set(TrustHeaders.CORRELATION_ID, correlationId);
            }

            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Governance scope call non-success: {}", response.getStatusCode());
                return true;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            boolean allowed = data.path("allowed").asBoolean(true);
            if (!allowed) {
                log.info("Governance denied facility scope: reason={}", data.path("reason").asText());
            }
            return allowed;
        } catch (RestClientException e) {
            log.warn("Governance scope call failed (degraded allow): {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.warn("Governance scope parse failed (degraded allow): {}", e.getMessage());
            return true;
        }
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
