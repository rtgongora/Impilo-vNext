package zw.gov.mohcc.impilo.tshepo.keys.core;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Builds an RFC 7517 JSON Web Key Set (JWKS) from all ACTIVE signing keys.
 *
 * <p>The JWKS contains OKP (Octet Key Pair) entries with Ed25519 curve,
 * suitable for EdDSA signature verification. Private key material is
 * never included in the JWKS output.</p>
 *
 * <p>JWKS responses are cached in Redis for performance.</p>
 */
@Service
public class JwksService {

    private static final Logger log = LoggerFactory.getLogger(JwksService.class);
    private static final String JWKS_CACHE_KEY = "tshepo-keys:jwks";

    private final SigningKeyRepository signingKeyRepository;
    private final StringRedisTemplate redisTemplate;
    private final KeysProperties keysProperties;

    public JwksService(SigningKeyRepository signingKeyRepository,
                       StringRedisTemplate redisTemplate,
                       KeysProperties keysProperties) {
        this.signingKeyRepository = signingKeyRepository;
        this.redisTemplate = redisTemplate;
        this.keysProperties = keysProperties;
    }

    /**
     * Get the JWKS JSON string. Returns cached version if available.
     *
     * @return RFC 7517 JWKS JSON string
     */
    public String getJwksJson() {
        // Try cache first
        try {
            String cached = redisTemplate.opsForValue().get(JWKS_CACHE_KEY);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis cache miss for JWKS (redis may be unavailable): {}", e.getMessage());
        }

        // Build JWKS from database
        String jwksJson = buildJwksJson();

        // Cache the result
        try {
            redisTemplate.opsForValue().set(
                    JWKS_CACHE_KEY,
                    jwksJson,
                    Duration.ofSeconds(keysProperties.getJwksCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("Failed to cache JWKS in Redis: {}", e.getMessage());
        }

        return jwksJson;
    }

    /**
     * Build the JWKS JSON from all ACTIVE keys in the database.
     */
    private String buildJwksJson() {
        List<SigningKeyEntity> activeKeys = signingKeyRepository.findAllActiveKeys();

        List<JWK> jwkList = activeKeys.stream()
                .map(this::toOctetKeyPair)
                .map(okp -> (JWK) okp)
                .toList();

        JWKSet jwkSet = new JWKSet(jwkList);

        // Use toPublicJWKSet to ensure no private key material leaks
        return jwkSet.toPublicJWKSet().toJSONObject().toString();
    }

    /**
     * Convert a SigningKeyEntity to a Nimbus OKP (public key only) for JWKS.
     */
    private OctetKeyPair toOctetKeyPair(SigningKeyEntity entity) {
        byte[] publicKeyBytes = decodePublicKeyPem(entity.getPublicKeyPem());

        return new OctetKeyPair.Builder(
                Curve.Ed25519,
                Base64URL.encode(publicKeyBytes))
                .keyID(entity.getKeyId())
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }

    /**
     * Invalidate the cached JWKS (called after key rotation or generation).
     */
    public void invalidateCache() {
        try {
            redisTemplate.delete(JWKS_CACHE_KEY);
            log.debug("JWKS cache invalidated");
        } catch (Exception e) {
            log.warn("Failed to invalidate JWKS cache: {}", e.getMessage());
        }
    }

    /**
     * Get the JWKS as a parsed Map (for controller responses that need structured JSON).
     */
    public Map<String, Object> getJwksMap() {
        List<SigningKeyEntity> activeKeys = signingKeyRepository.findAllActiveKeys();

        List<JWK> jwkList = activeKeys.stream()
                .map(this::toOctetKeyPair)
                .map(okp -> (JWK) okp)
                .toList();

        JWKSet jwkSet = new JWKSet(jwkList);

        return jwkSet.toPublicJWKSet().toJSONObject();
    }

    private byte[] decodePublicKeyPem(String pem) {
        String base64 = pem
                .replace("-----BEGIN ED25519 PUBLIC KEY-----", "")
                .replace("-----END ED25519 PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
