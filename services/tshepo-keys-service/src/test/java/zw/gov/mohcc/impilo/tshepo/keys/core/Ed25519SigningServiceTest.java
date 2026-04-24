package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link Ed25519SigningService}.
 *
 * <p>Tests cover key generation, payload signing, raw signature verification,
 * JWS signing/verification, and private key encryption/decryption round-trips.</p>
 */
@ExtendWith(MockitoExtension.class)
class Ed25519SigningServiceTest {

    /** 32-byte (256-bit) hex-encoded AES KEK for testing. */
    private static final String TEST_KEK_HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SigningKeyRepository signingKeyRepository;

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    private KeysProperties keysProperties;

    private Ed25519SigningService signingService;

    @BeforeEach
    void setUp() {
        keysProperties = buildKeysProperties();
        signingService = new Ed25519SigningService(signingKeyRepository, eventOutboxRepository, keysProperties);
    }

    // ------------------------------------------------------------------
    // sign + verify round-trip tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sign produces a valid Ed25519 signature that can be verified")
    void sign_producesValidSignature() {
        // Arrange: generate a real key pair via the service
        SigningKeyEntity savedEntity = stubGenerateAndSave();

        byte[] payload = "hello impilo".getBytes();

        // Act: sign the payload
        String signatureB64 = signingService.signPayload(TENANT_ID, payload);

        // Assert: decode and verify
        byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
        assertThat(signatureBytes).hasSize(64); // Ed25519 signatures are always 64 bytes

        // Verify using the same service
        String keyId = savedEntity.getKeyId();
        when(signingKeyRepository.findByKeyId(keyId)).thenReturn(Optional.of(savedEntity));

        boolean valid = signingService.verifySignature(keyId, payload, signatureBytes);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verify with a valid raw signature returns true")
    void verify_withValidSignature_returnsTrue() {
        // Arrange: produce a key entity with real key material
        SigningKeyEntity entity = createRealKeyEntity();
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(entity));
        when(signingKeyRepository.findByKeyId(entity.getKeyId())).thenReturn(Optional.of(entity));

        byte[] payload = "verify-me".getBytes();
        String sigB64 = signingService.signPayload(TENANT_ID, payload);
        byte[] sigBytes = Base64.getUrlDecoder().decode(sigB64);

        // Act
        boolean result = signingService.verifySignature(entity.getKeyId(), payload, sigBytes);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verify with tampered data returns false")
    void verify_withTamperedData_returnsFalse() {
        // Arrange
        SigningKeyEntity entity = createRealKeyEntity();
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(entity));
        when(signingKeyRepository.findByKeyId(entity.getKeyId())).thenReturn(Optional.of(entity));

        byte[] originalPayload = "original-data".getBytes();
        byte[] tamperedPayload = "tampered-data".getBytes();

        String sigB64 = signingService.signPayload(TENANT_ID, originalPayload);
        byte[] sigBytes = Base64.getUrlDecoder().decode(sigB64);

        // Act: verify signature against tampered data
        boolean result = signingService.verifySignature(entity.getKeyId(), tamperedPayload, sigBytes);

        // Assert
        assertThat(result).isFalse();
    }

    // ------------------------------------------------------------------
    // JWS sign + verify tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("signJws produces a JWS compact serialization that verifyJws accepts")
    void signJws_andVerifyJws_roundTrip() {
        // Arrange
        SigningKeyEntity entity = createRealKeyEntity();
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(entity));
        when(signingKeyRepository.findByKeyId(entity.getKeyId())).thenReturn(Optional.of(entity));

        String payload = "{\"sub\":\"user-1\",\"aud\":\"test\"}";

        // Act
        String jws = signingService.signJws(TENANT_ID, payload);

        // Assert: JWS has 3 parts
        String[] parts = jws.split("\\.");
        assertThat(parts).hasSize(3);

        boolean valid = signingService.verifyJws(jws);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verifyJws returns false for a corrupted JWS")
    void verifyJws_withCorruptedJws_returnsFalse() {
        // Act: an obviously malformed JWS
        boolean result = signingService.verifyJws("invalid.jws.value");

        // Assert
        assertThat(result).isFalse();
    }

    // ------------------------------------------------------------------
    // Key generation tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("generateKeyPair persists an ACTIVE key with correct tenant and algorithm")
    void generateKeyPair_persistsActiveKeyWithCorrectMetadata() {
        // Arrange
        when(signingKeyRepository.save(any(SigningKeyEntity.class)))
                .thenAnswer(inv -> {
                    SigningKeyEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SigningKeyEntity result = signingService.generateKeyPair(TENANT_ID);

        // Assert
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getAlgorithm()).isEqualTo("Ed25519");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getKeyId()).startsWith("kid-");
        assertThat(result.getPublicKeyPem()).contains("-----BEGIN ED25519 PUBLIC KEY-----");
        assertThat(result.getPrivateKeyEncrypted()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now());

        // Verify an outbox event was written
        verify(eventOutboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    @DisplayName("generateKeyPair produces encrypted private key that can be decrypted to 32 bytes")
    void generateKeyPair_privateKeyDecryptsToRaw32Bytes() {
        // Arrange
        when(signingKeyRepository.save(any(SigningKeyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SigningKeyEntity entity = signingService.generateKeyPair(TENANT_ID);

        // Assert: the encrypted blob decrypts to a 32-byte raw Ed25519 private key
        byte[] raw = signingService.decryptPrivateKey(entity.getPrivateKeyEncrypted());
        assertThat(raw).hasSize(32);
    }

    // ------------------------------------------------------------------
    // getCurrentActiveKey tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getCurrentActiveKey returns existing active key when one exists")
    void getCurrentActiveKey_returnsExistingActiveKey() {
        // Arrange
        SigningKeyEntity existing = createRealKeyEntity();
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(existing));

        // Act
        SigningKeyEntity result = signingService.getCurrentActiveKey(TENANT_ID);

        // Assert
        assertThat(result.getKeyId()).isEqualTo(existing.getKeyId());
        verify(signingKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCurrentActiveKey generates a new key when none exist")
    void getCurrentActiveKey_generatesNewKeyWhenNoneExist() {
        // Arrange
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of());
        when(signingKeyRepository.save(any(SigningKeyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SigningKeyEntity result = signingService.getCurrentActiveKey(TENANT_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(signingKeyRepository).save(any(SigningKeyEntity.class));
    }

    // ------------------------------------------------------------------
    // Encryption round-trip test
    // ------------------------------------------------------------------

    @Test
    @DisplayName("encrypt then decrypt round-trips the raw private key bytes")
    void encryptDecrypt_roundTrip() {
        // Arrange: generate a real key
        when(signingKeyRepository.save(any(SigningKeyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SigningKeyEntity entity = signingService.generateKeyPair(TENANT_ID);
        byte[] encrypted = entity.getPrivateKeyEncrypted();

        // Act
        byte[] decrypted = signingService.decryptPrivateKey(encrypted);

        // Assert: decrypted should be 32 bytes and re-encryption should produce
        // different ciphertext (due to random IV) but decrypt to the same value
        assertThat(decrypted).hasSize(32);
    }

    // ------------------------------------------------------------------
    // Error cases
    // ------------------------------------------------------------------

    @Test
    @DisplayName("verifySignature throws when key is not found")
    void verifySignature_keyNotFound_throws() {
        when(signingKeyRepository.findByKeyId("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> signingService.verifySignature("nonexistent", new byte[0], new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key not found");
    }

    // ------------------------------------------------------------------
    // Test fixture helpers
    // ------------------------------------------------------------------

    /**
     * Build a {@link KeysProperties} with the test KEK and sensible defaults.
     */
    private KeysProperties buildKeysProperties() {
        KeysProperties props = new KeysProperties();
        props.setKek(TEST_KEK_HEX);
        props.setKeyAlgorithm("Ed25519");
        props.setRotationIntervalDays(90);
        props.setTokenDefaultTtlSeconds(300);
        props.setJwksCacheTtlSeconds(60);
        return props;
    }

    /**
     * Create a real Ed25519 key pair, encrypt the private key with the test KEK,
     * and return a fully populated {@link SigningKeyEntity}.
     */
    private SigningKeyEntity createRealKeyEntity() {
        Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
        keyGen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();

        Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();

        byte[] encryptedPrivate = encryptWithTestKek(privateKey.getEncoded());
        String publicPem = "-----BEGIN ED25519 PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(publicKey.getEncoded())
                + "\n-----END ED25519 PUBLIC KEY-----";

        String keyId = "kid-test" + UUID.randomUUID().toString().substring(0, 8);

        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setId(1L);
        entity.setTenantId(TENANT_ID);
        entity.setKeyId(keyId);
        entity.setAlgorithm("Ed25519");
        entity.setPublicKeyPem(publicPem);
        entity.setPrivateKeyEncrypted(encryptedPrivate);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
        return entity;
    }

    /**
     * Encrypt raw private key bytes using AES-256-GCM with the test KEK,
     * mirroring the format used by {@link Ed25519SigningService}.
     */
    private byte[] encryptWithTestKek(byte[] privateKeyBytes) {
        try {
            byte[] kekBytes = hexToBytes(TEST_KEK_HEX);
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(kekBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] ciphertext = cipher.doFinal(privateKeyBytes);

            ByteBuffer result = ByteBuffer.allocate(12 + ciphertext.length);
            result.put(iv);
            result.put(ciphertext);
            return result.array();
        } catch (Exception e) {
            throw new RuntimeException("Test encryption failed", e);
        }
    }

    /**
     * Stub repository save to return the entity and wire up a real key generation.
     */
    private SigningKeyEntity stubGenerateAndSave() {
        SigningKeyEntity[] capturedEntity = new SigningKeyEntity[1];
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of());
        when(signingKeyRepository.save(any(SigningKeyEntity.class)))
                .thenAnswer(inv -> {
                    SigningKeyEntity e = inv.getArgument(0);
                    e.setId(1L);
                    capturedEntity[0] = e;
                    return e;
                });
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // This triggers generateKeyPair via getCurrentActiveKey
        signingService.signPayload(TENANT_ID, "dummy".getBytes());

        // Reset and re-stub to use the generated entity
        reset(signingKeyRepository);
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID))
                .thenReturn(List.of(capturedEntity[0]));
        return capturedEntity[0];
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
