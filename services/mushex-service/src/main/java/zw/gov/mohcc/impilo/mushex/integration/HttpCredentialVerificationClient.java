package zw.gov.mohcc.impilo.mushex.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpCredentialVerificationClient implements CredentialVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCredentialVerificationClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public HttpCredentialVerificationClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.credential-base-url:http://localhost:8094}") String baseUrl,
            @Value("${impilo.services.credential-payee-verification-enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public CredentialVerificationResult verifyPayee(UUID tenantId, String providerId) {
        if (!enabled) {
            log.debug("Payee credential verification disabled; skipping");
            return new CredentialVerificationResult(true, "SKIPPED", null, null);
        }
        if (tenantId == null || providerId == null || providerId.isBlank()) {
            throw new IllegalStateException("tenantId and providerId are required for credential verification");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", tenantId.toString());
        body.put("provider_id", providerId);

        try {
            String raw = restClient.post()
                    .uri("/v1/credentials/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Empty credential verification response");
            }
            JsonNode node = objectMapper.readTree(raw);
            boolean valid = node.path("valid").asBoolean(false);
            String status = node.path("status").asText("UNKNOWN");
            String verificationRef = node.path("verificationRef").asText("");
            String credentialId = node.has("credentialId") && !node.get("credentialId").isNull()
                    ? node.get("credentialId").asText()
                    : null;
            if (credentialId != null && credentialId.isEmpty()) {
                credentialId = null;
            }
            log.info("Payee credential verification tenantId={} providerId={} valid={} status={} verificationRef={}",
                    tenantId, providerId, valid, status, verificationRef);
            return new CredentialVerificationResult(valid, status, verificationRef, credentialId);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Credential verification call failed tenantId={} providerId={}", tenantId, providerId, e);
            throw new IllegalStateException("Credential verification service call failed: " + e.getMessage(), e);
        }
    }
}
