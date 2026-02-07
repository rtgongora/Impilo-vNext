package zw.gov.mohcc.impilo.tshepo.keys.core;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
import java.util.UUID;

/**
 * Core service for Ed25519 key generation, storage, signing, and verification.
 *
 * <p>Private keys are encrypted at rest using AES-256-GCM with a KEK sourced from
 * configuration (Vault-ready). The encrypted format is:
 * {@code [12-byte IV | encrypted-private-key | 16-byte GCM-tag]}</p>
 *
 * <p>JWS compact serialization is done via Nimbus JOSE+JWT using Ed25519 (EdDSA).</p>
 */
@Service
public class Ed25519SigningService {

    private static final Logger log = LoggerFactory.getLogger(Ed25519SigningService.class);

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final SigningKeyRepository signingKeyRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final KeysProperties keysProperties;
    private final SecureRandom secureRandom;
    private final byte[] kekBytes;

    public Ed25519SigningService(SigningKeyRepository signingKeyRepository,
                                 EventOutboxRepository eventOutboxRepository,
                                 KeysProperties keysProperties) {
        this.signingKeyRepository = signingKeyRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.keysProperties = keysProperties;
        this.secureRandom = new SecureRandom();
        this.kekBytes = hexToBytes(keysProperties.getKek());

        if (this.kekBytes.length != 32) {
            throw new IllegalStateException("KEK must be exactly 32 bytes (256 bits) for AES-256-GCM, got " + this.kekBytes.length);
        }
    }

    /**
     * Generate a new Ed25519 key pair, encrypt the private key, and persist it.
     *
     * @param tenantId the tenant this key belongs to
     * @return the persisted SigningKeyEntity (with encrypted private key)
     */
    public SigningKeyEntity generateKeyPair(UUID tenantId) {
        // Generate Ed25519 key pair using Bouncy Castle
        Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
        keyGen.init(new Ed25519KeyGenerationParameters(secureRandom));
        AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();

        Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();

        // Generate a unique key ID
        String keyId = "kid-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Encode public key as PEM (SubjectPublicKeyInfo format via Base64)
        String publicKeyPem = encodePublicKeyPem(publicKey.getEncoded());

        // Encrypt private key with AES-256-GCM
        byte[] encryptedPrivateKey = encryptPrivateKey(privateKey.getEncoded());

        // Compute expiry based on rotation interval
        Instant expiresAt = Instant.now().plus(keysProperties.getRotationIntervalDays(), ChronoUnit.DAYS);

        // Persist the key
        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setTenantId(tenantId);
        entity.setKeyId(keyId);
        entity.setAlgorithm(keysProperties.getKeyAlgorithm());
        entity.setPublicKeyPem(publicKeyPem);
        entity.setPrivateKeyEncrypted(encryptedPrivateKey);
        entity.setStatus("ACTIVE");
        entity.setExpiresAt(expiresAt);
        entity = signingKeyRepository.save(entity);

        // Write outbox event
        writeOutboxEvent("SigningKey", keyId, "KEY_GENERATED",
                "{\"keyId\":\"" + keyId + "\",\"tenantId\":\"" + tenantId + "\",\"algorithm\":\"Ed25519\"}");

        log.info("Generated new Ed25519 key pair: keyId={}, tenantId={}", keyId, tenantId);
        return entity;
    }

    /**
     * Get the current active signing key for a tenant. If none exist, generates one.
     */
    public SigningKeyEntity getCurrentActiveKey(UUID tenantId) {
        List<SigningKeyEntity> activeKeys = signingKeyRepository.findActiveKeysByTenant(tenantId);
        if (activeKeys.isEmpty()) {
            log.info("No active key found for tenant {}, generating a new one", tenantId);
            return generateKeyPair(tenantId);
        }
        return activeKeys.get(0); // Most recently created active key
    }

    /**
     * Sign an arbitrary byte payload with the current active Ed25519 key (raw signature).
     *
     * @return Base64url-encoded Ed25519 signature
     */
    public String signPayload(UUID tenantId, byte[] payload) {
        SigningKeyEntity keyEntity = getCurrentActiveKey(tenantId);
        byte[] privateKeyBytes = decryptPrivateKey(keyEntity.getPrivateKeyEncrypted());

        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);
        org.bouncycastle.crypto.signers.Ed25519Signer signer = new org.bouncycastle.crypto.signers.Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(payload, 0, payload.length);
        byte[] signature = signer.generateSignature();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    /**
     * Sign a payload and produce a JWS compact serialization (EdDSA with Ed25519).
     *
     * @param tenantId the tenant whose key to use
     * @param payload  the payload string to sign
     * @return JWS compact serialization string
     */
    public String signJws(UUID tenantId, String payload) {
        SigningKeyEntity keyEntity = getCurrentActiveKey(tenantId);
        byte[] privateKeyBytes = decryptPrivateKey(keyEntity.getPrivateKeyEncrypted());
        byte[] publicKeyBytes = decodePublicKeyPem(keyEntity.getPublicKeyPem());

        try {
            // Build Nimbus OKP key for Ed25519
            OctetKeyPair jwk = new OctetKeyPair.Builder(
                    Curve.Ed25519,
                    Base64URL.encode(publicKeyBytes))
                    .d(Base64URL.encode(privateKeyBytes))
                    .keyID(keyEntity.getKeyId())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .keyID(keyEntity.getKeyId())
                    .build();

            JWSObject jwsObject = new JWSObject(header, new Payload(payload));
            jwsObject.sign(new Ed25519Signer(jwk));

            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to create JWS compact serialization", e);
        }
    }

    /**
     * Verify a raw Ed25519 signature against a payload and a specific key.
     */
    public boolean verifySignature(String keyId, byte[] payload, byte[] signature) {
        SigningKeyEntity keyEntity = signingKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

        byte[] publicKeyBytes = decodePublicKeyPem(keyEntity.getPublicKeyPem());
        Ed25519PublicKeyParameters publicKey = new Ed25519PublicKeyParameters(publicKeyBytes, 0);

        org.bouncycastle.crypto.signers.Ed25519Signer verifier = new org.bouncycastle.crypto.signers.Ed25519Signer();
        verifier.init(false, publicKey);
        verifier.update(payload, 0, payload.length);
        return verifier.verifySignature(signature);
    }

    /**
     * Verify a JWS compact serialization against the JWKS-published keys.
     */
    public boolean verifyJws(String jwsCompact) {
        try {
            JWSObject jwsObject = JWSObject.parse(jwsCompact);
            String keyId = jwsObject.getHeader().getKeyID();

            SigningKeyEntity keyEntity = signingKeyRepository.findByKeyId(keyId)
                    .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

            byte[] publicKeyBytes = decodePublicKeyPem(keyEntity.getPublicKeyPem());

            OctetKeyPair publicJwk = new OctetKeyPair.Builder(
                    Curve.Ed25519,
                    Base64URL.encode(publicKeyBytes))
                    .keyID(keyEntity.getKeyId())
                    .build();

            return jwsObject.verify(new Ed25519Verifier(publicJwk));
        } catch (Exception e) {
            log.warn("JWS verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Decrypt an Ed25519 private key from the encrypted at-rest format.
     *
     * @param encrypted format: [12-byte IV | ciphertext | GCM tag]
     * @return raw 32-byte Ed25519 private key
     */
    public byte[] decryptPrivateKey(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(kekBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt private key", e);
        }
    }

    /**
     * Encrypt an Ed25519 private key for at-rest storage.
     *
     * @param privateKeyBytes raw 32-byte Ed25519 private key
     * @return encrypted format: [12-byte IV | ciphertext | GCM tag]
     */
    private byte[] encryptPrivateKey(byte[] privateKeyBytes) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(kekBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(privateKeyBytes);

            // Prepend IV to ciphertext
            ByteBuffer result = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            result.put(iv);
            result.put(ciphertext);
            return result.array();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt private key", e);
        }
    }

    /**
     * Encode raw Ed25519 public key bytes as a simple Base64 PEM block.
     */
    private String encodePublicKeyPem(byte[] publicKeyBytes) {
        String base64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        return "-----BEGIN ED25519 PUBLIC KEY-----\n" + base64 + "\n-----END ED25519 PUBLIC KEY-----";
    }

    /**
     * Decode the raw Ed25519 public key bytes from a PEM block.
     */
    byte[] decodePublicKeyPem(String pem) {
        String base64 = pem
                .replace("-----BEGIN ED25519 PUBLIC KEY-----", "")
                .replace("-----END ED25519 PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private void writeOutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        eventOutboxRepository.save(event);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("KEK hex string must not be null or empty");
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
