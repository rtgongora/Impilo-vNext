package zw.gov.mohcc.impilo.vito.keys;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * W4b: outbound client to the sovereign {@code tshepo-keys-service} for QR-token signing.
 *
 * <p>Active only when {@code vito.qr.signing-source=tshepo-keys}. It replaces VITO's local
 * seed-derived Ed25519 key with a governed, custody-hardened key: the private key lives in
 * tshepo-keys (software AES-GCM today, HSM later) and never enters this process. Signing is a
 * {@code POST /v1/sign} (purpose {@code QR_SIGNING}, {@code jwsCompact=true}) returning a compact
 * JWS whose {@code kid} verifiers resolve against the published JWKS ({@code GET /v1/keys/jwks}).</p>
 */
@Component
@ConditionalOnProperty(name = "vito.qr.signing-source", havingValue = "tshepo-keys")
public class TshepoKeysSigningClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoKeysSigningClient.class);

    private final RestClient restClient;

    public TshepoKeysSigningClient(
            @Value("${vito.tshepo-keys.base-url:http://tshepo-keys-service:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        log.info("VITO QR signing repointed to tshepo-keys at {}", baseUrl);
    }

    /**
     * Sign a QR claims payload as a compact JWS via tshepo-keys (purpose QR_SIGNING).
     *
     * @return compact JWS serialization (kid header set by tshepo-keys)
     */
    public String signJws(String tenantId, String payload) {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId,
                "payload", payload,
                "jwsCompact", true,
                "purpose", "QR_SIGNING");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("signature") == null) {
            throw new IllegalStateException("tshepo-keys /v1/sign returned no signature");
        }
        return (String) response.get("signature");
    }

    /**
     * Resolve the EdDSA verifier for a kid by fetching the JWKS from tshepo-keys.
     * Returns empty if the kid is unknown (e.g. revoked/expired and dropped from the JWKS).
     */
    public Optional<JWSVerifier> resolveVerifier(String kid) {
        try {
            String jwksJson = restClient.get()
                    .uri("/v1/keys/jwks")
                    .retrieve()
                    .body(String.class);
            if (jwksJson == null) {
                return Optional.empty();
            }
            JWKSet jwkSet = JWKSet.parse(jwksJson);
            JWK jwk = jwkSet.getKeyByKeyId(kid);
            if (jwk == null || !(jwk instanceof OctetKeyPair)) {
                return Optional.empty();
            }
            return Optional.of(new Ed25519Verifier(((OctetKeyPair) jwk).toPublicJWK()));
        } catch (Exception e) {
            log.warn("Failed to resolve verifier for kid {} from tshepo-keys JWKS: {}", kid, e.getMessage());
            return Optional.empty();
        }
    }
}
