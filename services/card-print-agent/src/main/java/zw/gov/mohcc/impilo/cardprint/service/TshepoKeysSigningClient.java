package zw.gov.mohcc.impilo.cardprint.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * W4b: outbound client to the sovereign {@code tshepo-keys-service} for card-assertion signing.
 *
 * <p>Active only when {@code card-print.qr.signing-source=tshepo-keys}. It replaces the print
 * agent's local seed-derived Ed25519 key with a governed, custody-hardened key: the private key
 * lives in tshepo-keys and never enters the agent. Signing is a {@code POST /v1/sign} (purpose
 * {@code CARD_ASSERTION}, raw Ed25519 signature) returning {@code {keyId, signature}}; verifiers
 * resolve the public key from the published JWKS ({@code GET /v1/keys/jwks}) by the kid.</p>
 */
@Component
@ConditionalOnProperty(name = "card-print.qr.signing-source", havingValue = "tshepo-keys")
public class TshepoKeysSigningClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoKeysSigningClient.class);

    private final RestClient restClient;
    private final String tenantId;

    public TshepoKeysSigningClient(
            @Value("${card-print.tshepo-keys.base-url:http://tshepo-keys-service:8081}") String baseUrl,
            @Value("${card-print.tshepo-keys.tenant-id:}") String tenantId) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.tenantId = tenantId;
        log.info("Card QR signing repointed to tshepo-keys at {}", baseUrl);
    }

    /** The signing result: the kid actually used and the raw Base64url Ed25519 signature. */
    public record SignResult(String keyId, String signature) {}

    /**
     * Sign a canonical card-assertion payload via tshepo-keys (purpose CARD_ASSERTION).
     *
     * @param tenantOverride tenant to sign under; falls back to configured tenant when blank
     * @param payloadJson    canonical assertion JSON to sign
     */
    public SignResult sign(String tenantOverride, String payloadJson) {
        String effectiveTenant = (tenantOverride != null && !tenantOverride.isBlank())
                ? tenantOverride : tenantId;
        Map<String, Object> request = Map.of(
                "tenantId", effectiveTenant,
                "payload", payloadJson,
                "jwsCompact", false,
                "purpose", "CARD_ASSERTION");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("signature") == null || response.get("keyId") == null) {
            throw new IllegalStateException("tshepo-keys /v1/sign returned no signature/keyId");
        }
        return new SignResult((String) response.get("keyId"), (String) response.get("signature"));
    }
}
