package zw.gov.mohcc.impilo.tshepo.offline.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
     * Sign the given payload using Ed25519 via the keys-service.
     *
     * @param payload  the JSON string payload to sign
     * @param keyId    the key identifier to use for signing
     * @return JWS compact serialization string
     */
    public String signPayload(String payload, String keyId) {
        log.debug("Requesting JWS signature from keys-service for keyId={}", keyId);

        SignRequest request = new SignRequest(payload, keyId, "EdDSA");
        SignResponse response = keysRestClient.post()
                .uri("/v1/sign/jws")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SignResponse.class);

        if (response == null || response.jws() == null) {
            throw new RuntimeException("Keys service returned null JWS for keyId=" + keyId);
        }
        return response.jws();
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SignRequest(String payload, String keyId, String algorithm) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignResponse(String jws, String keyId, String algorithm) {}
}
