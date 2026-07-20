package zw.gov.mohcc.impilo.tshepo.keys.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwksService}.
 *
 * <p>Tests verify that the JWKS endpoint returns only public key material,
 * respects Redis caching, and correctly builds OKP entries for Ed25519 keys.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwksServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SigningKeyRepository signingKeyRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private KeysProperties keysProperties;
    private JwksService jwksService;

    @BeforeEach
    void setUp() {
        keysProperties = buildKeysProperties();
        jwksService = new JwksService(signingKeyRepository, redisTemplate, keysProperties);
    }

    // ------------------------------------------------------------------
    // getJwks — returns active public keys
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getJwks returns JWKS containing only active public keys")
    void getJwks_returnsActivePublicKeys() throws Exception {
        // Arrange: two active keys in the database
        SigningKeyEntity key1 = buildKeyEntity("kid-jwks-key0001", generateFakeEd25519PublicKeyPem());
        SigningKeyEntity key2 = buildKeyEntity("kid-jwks-key0002", generateFakeEd25519PublicKeyPem());
        when(signingKeyRepository.findAllVerificationKeys(any(Instant.class))).thenReturn(List.of(key1, key2));

        // Redis cache miss
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        String jwksJson = jwksService.getJwksJson();

        // Assert: parse the JWKS and verify it contains 2 keys
        Map<String, Object> jwks = MAPPER.readValue(jwksJson, new TypeReference<>() {});
        assertThat(jwks).containsKey("keys");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(2);

        // Verify each key has the expected OKP fields
        for (Map<String, Object> key : keys) {
            assertThat(key.get("kty")).isEqualTo("OKP");
            assertThat(key.get("crv")).isEqualTo("Ed25519");
            assertThat(key.get("use")).isEqualTo("sig");
            assertThat(key).containsKey("kid");
            assertThat(key).containsKey("x"); // public key coordinate
        }
    }

    @Test
    @DisplayName("getJwks excludes private key material (no 'd' parameter in output)")
    void getJwks_excludesPrivateKeyMaterial() throws Exception {
        // Arrange
        SigningKeyEntity key = buildKeyEntity("kid-jwks-noprivk", generateFakeEd25519PublicKeyPem());
        when(signingKeyRepository.findAllVerificationKeys(any(Instant.class))).thenReturn(List.of(key));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        String jwksJson = jwksService.getJwksJson();

        // Assert: the 'd' parameter (private key) must not be present
        Map<String, Object> jwks = MAPPER.readValue(jwksJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0)).doesNotContainKey("d");
    }

    // ------------------------------------------------------------------
    // getJwks — Redis caching behavior
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getJwks returns cached value from Redis when available")
    void getJwks_returnsCachedValueFromRedis() {
        // Arrange: cached JWKS in Redis
        String cachedJwks = "{\"keys\":[{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"kid\":\"cached\"}]}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedJwks);

        // Act
        String result = jwksService.getJwksJson();

        // Assert: returned cached value, did not query database
        assertThat(result).isEqualTo(cachedJwks);
        verify(signingKeyRepository, never()).findAllVerificationKeys(any(Instant.class));
    }

    @Test
    @DisplayName("getJwks falls through to database when Redis is unavailable")
    void getJwks_fallsThroughToDatabaseOnRedisFailure() throws Exception {
        // Arrange: Redis throws on read
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // Database returns one key
        SigningKeyEntity key = buildKeyEntity("kid-redis-fail01", generateFakeEd25519PublicKeyPem());
        when(signingKeyRepository.findAllVerificationKeys(any(Instant.class))).thenReturn(List.of(key));

        // Act
        String jwksJson = jwksService.getJwksJson();

        // Assert: JWKS was built from database despite Redis failure
        Map<String, Object> jwks = MAPPER.readValue(jwksJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);
    }

    @Test
    @DisplayName("getJwks returns empty keys array when no active keys exist")
    void getJwks_noActiveKeys_returnsEmptyKeysArray() throws Exception {
        // Arrange
        when(signingKeyRepository.findAllVerificationKeys(any(Instant.class))).thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        String jwksJson = jwksService.getJwksJson();

        // Assert
        Map<String, Object> jwks = MAPPER.readValue(jwksJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).isEmpty();
    }

    // ------------------------------------------------------------------
    // getJwksMap tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getJwksMap returns a Map representation of the JWKS")
    void getJwksMap_returnsMapWithKeysArray() {
        // Arrange
        SigningKeyEntity key = buildKeyEntity("kid-map-test0001", generateFakeEd25519PublicKeyPem());
        when(signingKeyRepository.findAllVerificationKeys(any(Instant.class))).thenReturn(List.of(key));

        // Act
        Map<String, Object> jwksMap = jwksService.getJwksMap();

        // Assert
        assertThat(jwksMap).containsKey("keys");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwksMap.get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).get("kid")).isEqualTo("kid-map-test0001");
    }

    // ------------------------------------------------------------------
    // invalidateCache tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("invalidateCache deletes the JWKS cache key from Redis")
    void invalidateCache_deletesRedisCacheKey() {
        // Arrange
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // Act
        jwksService.invalidateCache();

        // Assert
        verify(redisTemplate).delete("tshepo-keys:jwks");
    }

    @Test
    @DisplayName("invalidateCache handles Redis failure gracefully")
    void invalidateCache_handlesRedisFailureGracefully() {
        // Arrange
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // Act: should not throw
        jwksService.invalidateCache();

        // Assert: method completed without exception
        verify(redisTemplate).delete("tshepo-keys:jwks");
    }

    // ------------------------------------------------------------------
    // Test fixture helpers
    // ------------------------------------------------------------------

    private KeysProperties buildKeysProperties() {
        KeysProperties props = new KeysProperties();
        props.setKek("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        props.setKeyAlgorithm("Ed25519");
        props.setRotationIntervalDays(90);
        props.setTokenDefaultTtlSeconds(300);
        props.setJwksCacheTtlSeconds(60);
        return props;
    }

    /**
     * Build a {@link SigningKeyEntity} with the given key ID and public key PEM.
     */
    private SigningKeyEntity buildKeyEntity(String keyId, String publicKeyPem) {
        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setId(1L);
        entity.setTenantId(TENANT_ID);
        entity.setKeyId(keyId);
        entity.setAlgorithm("Ed25519");
        entity.setPublicKeyPem(publicKeyPem);
        entity.setPrivateKeyEncrypted(new byte[60]);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        entity.setExpiresAt(Instant.now().plus(80, ChronoUnit.DAYS));
        return entity;
    }

    /**
     * Generate a fake but structurally valid Ed25519 public key PEM.
     * The key material is 32 random bytes encoded as Base64, wrapped in PEM headers.
     */
    private String generateFakeEd25519PublicKeyPem() {
        byte[] fakePublicKey = new byte[32];
        new java.security.SecureRandom().nextBytes(fakePublicKey);
        String base64 = Base64.getEncoder().encodeToString(fakePublicKey);
        return "-----BEGIN ED25519 PUBLIC KEY-----\n" + base64 + "\n-----END ED25519 PUBLIC KEY-----";
    }
}
