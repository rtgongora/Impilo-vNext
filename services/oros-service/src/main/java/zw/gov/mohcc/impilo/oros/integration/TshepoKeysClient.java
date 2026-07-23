package zw.gov.mohcc.impilo.oros.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.oros.core.OrosDomainException;

import java.util.Map;
import java.util.UUID;

/**
 * Client for tshepo-keys-service (OF-B2, Vol II §8.2/§13.2): detached JWS signing
 * of prescription versions and prescription tokens with purpose-scoped Ed25519 keys.
 *
 * <p><b>Fail-closed</b>: purpose-scoped keys must be explicitly provisioned per tenant
 * (tshepo-keys never auto-generates non-GENERAL keys). A signing failure surfaces as
 * {@code SIGNING_UNAVAILABLE} — an unsigned prescription can never activate.</p>
 */
@Component
public class TshepoKeysClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoKeysClient.class);

    /** §8.2 signing purpose; keys provisioned per tenant via tshepo-keys key management. */
    public static final String PURPOSE = "PRESCRIPTION_SIGNING";

    private final RestClient restClient;

    public record SignedJws(String keyId, String algorithm, String jws) {}

    public TshepoKeysClient(
            @Value("${oros.integration.tshepo-keys-url:http://tshepo-keys-service:8184}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Sign {@code payload} as a compact EdDSA JWS with the tenant's PRESCRIPTION_SIGNING key.
     * Verifiable offline against the published JWKS ({@code GET /v1/keys/jwks}).
     */
    public SignedJws signJws(UUID tenantId, String payload) {
        try {
            JsonNode response = restClient.post()
                    .uri("/v1/sign")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "tenantId", tenantId.toString(),
                            "payload", payload,
                            "jwsCompact", true,
                            "purpose", PURPOSE))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.path("signature").asText("").isBlank()) {
                throw new IllegalStateException("Empty signing response");
            }
            return new SignedJws(
                    response.path("keyId").asText(null),
                    response.path("algorithm").asText(null),
                    response.path("signature").asText());
        } catch (OrosDomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("tshepo-keys signing failed (purpose={}): {}", PURPOSE, e.getMessage());
            throw new OrosDomainException("SIGNING_UNAVAILABLE", 503,
                    "Prescription signing is unavailable — the prescription remains unsigned. "
                            + "A PRESCRIPTION_SIGNING key must be provisioned for this tenant.");
        }
    }
}
