package zw.gov.mohcc.impilo.ndr.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the data-governance-service /internal/v1/governance/decide endpoint.
 * If governance is unreachable, throws GovernanceUnavailableException.
 * Never silently allows — NDR is not in the care execution path.
 */
@Component
public class GovernanceClient {

    private static final Logger log = LoggerFactory.getLogger(GovernanceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String governanceBaseUrl;

    public GovernanceClient(RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            @Value("${ndr.governance.base-url:http://localhost:8220}") String governanceBaseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.governanceBaseUrl = governanceBaseUrl;
    }

    public DecisionResult decide(String principalId, String dataset, String purposeOfUse,
                                  String tenantId, String podId, String correlationId) {
        String url = governanceBaseUrl + "/internal/v1/governance/decide";

        Map<String, Object> body = Map.of(
                "principal_id", principalId,
                "dataset", dataset,
                "purpose_of_use", purposeOfUse,
                "context", Map.of("tenant_id", tenantId, "pod_id", podId));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", tenantId);
        headers.set("X-Pod-ID", podId);
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", correlationId);
        headers.set("Idempotency-Key", "ndr-decide-" + UUID.randomUUID());

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            String decision = responseBody.get("decision").asText();
            String policyVersion = responseBody.get("policy_version").asText();
            List<String> reasonCodes = new ArrayList<>();
            if (responseBody.has("reason_codes") && responseBody.get("reason_codes").isArray()) {
                for (JsonNode code : responseBody.get("reason_codes")) {
                    reasonCodes.add(code.asText());
                }
            }
            int ttlSeconds = responseBody.has("ttl_seconds") ? responseBody.get("ttl_seconds").asInt() : 60;

            return new DecisionResult(decision, policyVersion, reasonCodes, ttlSeconds);

        } catch (RestClientException e) {
            log.error("Governance service unreachable at {}: {}", url, e.getMessage());
            throw new GovernanceUnavailableException("Governance service unreachable: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse governance response: {}", e.getMessage());
            throw new GovernanceUnavailableException("Failed to parse governance response: " + e.getMessage(), e);
        }
    }

    public record DecisionResult(
            String decision,
            String policyVersion,
            List<String> reasonCodes,
            int ttlSeconds
    ) {
        public boolean isAllowed() {
            return "ALLOW".equals(decision);
        }
    }

    public static class GovernanceUnavailableException extends RuntimeException {
        public GovernanceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
