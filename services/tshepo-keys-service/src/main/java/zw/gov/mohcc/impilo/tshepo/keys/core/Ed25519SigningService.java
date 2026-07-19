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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Core service for Ed25519 key generation, storage, signing, and verification.
 *
 * <p>Private-key custody is delegated to the configured
 * {@link zw.gov.mohcc.impilo.tshepo.keys.core.custody.KeyCustodyProvider}
 * (software AES-256-GCM by default; KMS/HSM via a vendor provider). This class
 * orchestrates key rows, purposes, kid allocation and JWS assembly.</p>
 *
 * <p>JWS compact serialization is done via Nimbus JOSE+JWT using Ed25519 (EdDSA).</p>
 */
@Service
public class Ed25519SigningService {

    private static final Logger log = LoggerFactory.getLogger(Ed25519SigningService.class);


    private final SigningKeyRepository signingKeyRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final KeysProperties keysProperties;
    private final zw.gov.mohcc.impilo.tshepo.keys.core.custody.KeyCustodyProvider custodyProvider;

    public Ed25519SigningService(SigningKeyRepository signingKeyRepository,
                                 EventOutboxRepository eventOutboxRepository,
                                 KeysProperties keysProperties,
                                 zw.gov.mohcc.impilo.tshepo.keys.core.custody.KeyCustodyProvider custodyProvider) {
        this.signingKeyRepository = signingKeyRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.keysProperties = keysProperties;
        this.custodyProvider = custodyProvider;
    }

    /**
     * Generate a new Ed25519 key pair, encrypt the private key, and persist it.
     *
     * @param tenantId the tenant this key belongs to
     * @return the persisted SigningKeyEntity (with encrypted private key)
     */
    public SigningKeyEntity generateKeyPair(UUID tenantId) {
        // Key material is minted under the custody provider (software AES-GCM
        // today; KMS/HSM handle under a vendor provider).
        var material = custodyProvider.generate();

        // Generate a unique key ID
        String keyId = "kid-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Encode public key as PEM (SubjectPublicKeyInfo format via Base64)
        String publicKeyPem = encodePublicKeyPem(material.publicKey());

        byte[] encryptedPrivateKey = material.custodyBlob();

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
     * Purpose-scoped, fail-closed active-key lookup. A key may only be used for the
     * purpose it was issued for. GENERAL may be auto-provisioned (backward compatible);
     * any sensitive purpose (step-up, offline-capability, permit, document-signer, VDHC)
     * MUST be explicitly provisioned and fails closed if absent — keys for trust-bearing
     * operations are never silently minted.
     */
    public SigningKeyEntity getActiveKeyForPurpose(UUID tenantId, KeyPurpose purpose) {
        KeyPurpose p = purpose == null ? KeyPurpose.GENERAL : purpose;
        List<SigningKeyEntity> keys = signingKeyRepository.findActiveKeysByTenantAndPurpose(tenantId, p.name());
        if (!keys.isEmpty()) {
            return keys.get(0);
        }
        if (p == KeyPurpose.GENERAL) {
            log.info("No active GENERAL key for tenant {}, generating one", tenantId);
            SigningKeyEntity key = generateKeyPair(tenantId);
            key.setPurpose(KeyPurpose.GENERAL.name());
            return signingKeyRepository.save(key);
        }
        writeOutboxEvent("SigningKey", tenantId.toString(), "KEY_LOOKUP_FAILED_CLOSED",
                "{\"tenantId\":\"" + tenantId + "\",\"purpose\":\"" + p.name() + "\"}");
        throw new IllegalStateException(
                "No active signing key for tenant " + tenantId + " purpose " + p.name()
                + " — purpose-scoped keys must be explicitly provisioned (fail-closed).");
    }

    /**
     * Sign an arbitrary byte payload with the current active Ed25519 key (raw signature).
     *
     * @return Base64url-encoded Ed25519 signature
     */
    public String signPayload(UUID tenantId, byte[] payload) {
        return signPayloadWithKey(getCurrentActiveKey(tenantId), payload);
    }

    /**
     * Sign a raw byte payload with a specific (already-resolved) key. Used by callers
     * that have resolved a purpose-scoped key via {@link #getActiveKeyForPurpose}.
     *
     * @return Base64url-encoded Ed25519 signature
     */
    public String signPayloadWithKey(SigningKeyEntity keyEntity, byte[] payload) {
        byte[] signature = custodyProvider.sign(keyEntity.getPrivateKeyEncrypted(), payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    /**
     * Sign a payload and produce a JWS compact serialization (EdDSA with Ed25519),
     * using the tenant's current active key (legacy/unscoped callers).
     *
     * @param tenantId the tenant whose key to use
     * @param payload  the payload string to sign
     * @return JWS compact serialization string
     */
    public String signJws(UUID tenantId, String payload) {
        return signJwsWithKey(getCurrentActiveKey(tenantId), payload);
    }

    /**
     * Sign a payload as JWS using the tenant's active key for a specific {@link KeyPurpose}.
     * Sensitive purposes are fail-closed: if no key is provisioned for the purpose this
     * throws (via {@link #getActiveKeyForPurpose}), so a trust-bearing artefact is never
     * signed with a general key it was not authorised for.
     */
    public String signJws(UUID tenantId, String payload, KeyPurpose purpose) {
        return signJwsWithKey(getActiveKeyForPurpose(tenantId, purpose), payload);
    }

    /**
     * Produce a JWS compact serialization for a specific (already-resolved) key. The JWS
     * {@code kid} header is the key's id, so verifiers can resolve it from the JWKS.
     */
    public String signJwsWithKey(SigningKeyEntity keyEntity, String payload) {
        // JWS assembly needs the raw key locally (Nimbus signer) — software
        // custody only; a KMS/HSM provider throws here, which is the residual
        // migration surface documented on KeyCustodyProvider.
        byte[] privateKeyBytes = custodyProvider.exportPrivate(keyEntity.getPrivateKeyEncrypted());
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
        return custodyProvider.exportPrivate(encrypted);
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

}
