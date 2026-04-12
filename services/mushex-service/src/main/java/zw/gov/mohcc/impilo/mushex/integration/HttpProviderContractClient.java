package zw.gov.mohcc.impilo.mushex.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Calls coverage-service internal provider-contract search (fail-open on transport errors).
 */
@Component
public class HttpProviderContractClient implements ProviderContractClient {

    private static final Logger log = LoggerFactory.getLogger(HttpProviderContractClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpProviderContractClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.coverage-base-url:http://localhost:8140}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasActiveContract(String tenantId, String providerId, String payerId) {
        if (tenantId == null || tenantId.isBlank()
                || providerId == null || providerId.isBlank()
                || payerId == null || payerId.isBlank()) {
            return true;
        }
        try {
            UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid tenantId for provider-contract check: {}", tenantId);
            return true;
        }

        String uri = UriComponentsBuilder.fromPath("/internal/v1/provider-contracts")
                .queryParam("provider_id", providerId)
                .queryParam("payer_id", payerId)
                .queryParam("status", "ACTIVE")
                .build()
                .encode()
                .toUriString();

        try {
            String raw = restClient.get()
                    .uri(uri)
                    .header("X-Tenant-ID", tenantId)
                    .header("X-Pod-ID", "mushex-service")
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                log.warn("Empty provider-contract response tenantId={} providerId={} payerId={} — treating as no contract",
                        tenantId, providerId, payerId);
                return false;
            }
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) {
                log.warn("Unexpected provider-contract payload (not array) tenantId={}", tenantId);
                return false;
            }
            boolean active = root.size() > 0;
            log.info("Provider-contract probe tenantId={} providerId={} payerId={} activeMatches={}",
                    tenantId, providerId, payerId, active);
            return active;
        } catch (Exception e) {
            log.warn("coverage-service unreachable for provider-contract check tenantId={} providerId={} payerId={} — fail-open (allow). {}",
                    tenantId, providerId, payerId, e.getMessage());
            return true;
        }
    }
}
