package zw.gov.mohcc.impilo.mushex.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class HttpFacilityCredentialVerifier implements FacilityCredentialVerifier {

    private static final Logger log = LoggerFactory.getLogger(HttpFacilityCredentialVerifier.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public HttpFacilityCredentialVerifier(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.credential-verification-base-url:http://localhost:8094}") String baseUrl,
            @Value("${impilo.services.credential-verification-enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public void assertFacilityPaymentAllowed(UUID tenantId, UUID facilityId) {
        if (!enabled) {
            log.debug("Credential verification disabled; skipping facility check");
            return;
        }
        if (tenantId == null || facilityId == null) {
            throw new IllegalStateException("tenantId and facilityId are required for credential verification");
        }
        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/internal/mushex/facilities/{facilityId}/credential-active")
                            .queryParam("tenantId", tenantId.toString())
                            .build(facilityId))
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Empty credential verification response");
            }
            JsonNode node = objectMapper.readTree(raw);
            boolean active = node.path("active").asBoolean(false);
            log.info("Facility credential check tenantId={} facilityId={} active={}", tenantId, facilityId, active);
            if (!active) {
                throw new IllegalStateException(
                        "Facility credential verification failed: no ACTIVE in-window FACILITY credential");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Credential verification call failed tenantId={} facilityId={}", tenantId, facilityId, e);
            throw new IllegalStateException("Credential verification service call failed: " + e.getMessage(), e);
        }
    }
}
