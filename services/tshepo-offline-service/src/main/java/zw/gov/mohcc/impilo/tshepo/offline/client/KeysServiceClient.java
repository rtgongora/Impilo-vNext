package zw.gov.mohcc.impilo.tshepo.offline.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Client for the TSHEPO Keys Service.
 * Used to sign capability tokens and offline packs with Ed25519 keys,
 * and to fetch the JWKS for verification.
 */
@Component
public class KeysServiceClient {

    private static final Logger log = LoggerFactory.getLogger(KeysServiceClient.class);

    private final RestClient keysRestClient;

    public KeysServiceClient(@Qualifier("keysRestClient") RestClient keysRestClient) {
        this.keysRestClient = keysRestClient;
    }

    /**
     * Sign the given payload as an Ed25519 JWS via the keys-service, using the tenant's
     * active key for the given {@link zw.gov.mohcc.impilo.tshepo.keys.core.KeyPurpose}.
     *
     * <p>Calls the real {@code POST /v1/sign} endpoint with {@code jwsCompact=true}. The
     * keys-service resolves the purpose-scoped key (fail-closed if none is provisioned),
     * so capability tokens / offline packs are bound to their own keys rather than a
     * shared general key.</p>
     *
     * @param tenantId the tenant whose purpose-scoped key signs the payload
     * @param payload  the JSON string payload to sign
     * @param purpose  the {@code KeyPurpose} name (e.g. OFFLINE_CAPABILITY, OFFLINE_PACK)
     * @return JWS compact serialization string
     */
    public String signPayload(UUID tenantId, String payload, String purpose) {
        log.debug("Requesting JWS signature from keys-service for tenant={}, purpose={}", tenantId, purpose);

        SignRequest request = new SignRequest(tenantId, payload, true, purpose);
        SignResponse response = keysRestClient.post()
                .uri("/v1/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SignResponse.class);

        if (response == null || response.signature() == null) {
            throw new RuntimeException(
                    "Keys service returned null signature for tenant=" + tenantId + ", purpose=" + purpose);
        }
        return response.signature();
    }

    /**
     * Fetch the JWKS (JSON Web Key Set) from the keys-service for token verification.
     *
     * @return JWKS JSON string
     */
    public String fetchJwks() {
        log.debug("Fetching JWKS from keys-service");

        return keysRestClient.get()
                .uri("/v1/keys/jwks")
                .retrieve()
                .body(String.class);
    }

    /** Mirrors keys-service {@code SignPayloadRequest}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SignRequest(UUID tenantId, String payload, boolean jwsCompact, String purpose) {}

    /** Mirrors keys-service {@code SignPayloadResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignResponse(String keyId, String algorithm, String signature) {}
}
