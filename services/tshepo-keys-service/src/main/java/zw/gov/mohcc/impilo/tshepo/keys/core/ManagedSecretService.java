package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.ManagedSecretEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.ManagedSecretRepository;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * W4a: custody for named secrets. Values are AES-256-GCM encrypted at rest with
 * the service KEK — the exact posture signing keys use ([12-byte IV | ciphertext |
 * GCM tag]) — so a caller references a secret by ref (e.g. a de-id dataset HMAC
 * secret) and never holds raw material in its own env/config. Resolve is
 * INTERNAL-only + audited (see {@code SecretController}).
 */
@Service
public class ManagedSecretService {

    private static final Logger log = LoggerFactory.getLogger(ManagedSecretService.class);
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ACTIVE = "ACTIVE";

    private final ManagedSecretRepository repository;
    private final byte[] kekBytes;
    private final SecureRandom random = new SecureRandom();

    public ManagedSecretService(ManagedSecretRepository repository, KeysProperties properties) {
        this.repository = repository;
        this.kekBytes = hexToBytes(properties.getKek());
        if (kekBytes.length != 32) {
            throw new IllegalStateException("tshepo.keys.kek must be a 32-byte (64 hex char) AES-256 KEK");
        }
    }

    /** Register or rotate a named secret (value encrypted at rest; plaintext never stored). */
    @Transactional
    public ManagedSecretEntity registerOrRotate(UUID tenantId, String secretRef, String purpose, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("secret value must not be blank");
        }
        ManagedSecretEntity entity = repository
                .findByTenantIdAndSecretRefAndStatus(tenantId, secretRef, ACTIVE)
                .orElseGet(ManagedSecretEntity::new);
        boolean rotate = entity.getId() != null;
        entity.setTenantId(tenantId);
        entity.setSecretRef(secretRef);
        entity.setPurpose(purpose);
        entity.setStatus(ACTIVE);
        entity.setValueEncrypted(encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
        if (rotate) {
            entity.setRotatedAt(OffsetDateTime.now());
        }
        ManagedSecretEntity saved = repository.save(entity);
        log.info("Managed secret {} for tenant {} (ref {}, purpose {})",
                rotate ? "rotated" : "registered", tenantId, secretRef, purpose);
        return saved;
    }

    /** Resolve a named secret to plaintext (INTERNAL callers only; caller audits access). */
    @Transactional(readOnly = true)
    public Optional<String> resolve(UUID tenantId, String secretRef) {
        return repository.findByTenantIdAndSecretRefAndStatus(tenantId, secretRef, ACTIVE)
                .map(e -> new String(decrypt(e.getValueEncrypted()), StandardCharsets.UTF_8));
    }

    // ── AES-256-GCM at rest (same format as SoftwareKeyCustodyProvider) ──

    private byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kekBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ct = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt managed secret", e);
        }
    }

    private byte[] decrypt(byte[] blob) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(blob);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ct = new byte[buffer.remaining()];
            buffer.get(ct);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kekBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt managed secret", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalStateException("tshepo.keys.kek is not configured");
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
