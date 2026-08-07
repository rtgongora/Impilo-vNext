package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipLinkRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipLinkResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipVerifyResponse;
import zw.gov.mohcc.impilo.tshepo.identity.config.IdentityProperties;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.MosipLinkEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.MosipLinkRepository;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Manages the encrypted, indirect MOSIP link store.
 *
 * <h3>Storage model:</h3>
 * <ul>
 *   <li>link_ref (MOSIP opaque token) is encrypted with AES-256-GCM before storage</li>
 *   <li>A SHA-256 hash of the link_ref is stored for fast lookup</li>
 *   <li>The KEK (Key Encryption Key) is loaded from configuration</li>
 * </ul>
 *
 * <h3>Verification workflow:</h3>
 * <ol>
 *   <li>Caller supplies (tenantId, healthId, linkRef)</li>
 *   <li>Service hashes the supplied linkRef and looks up the stored record</li>
 *   <li>If the hash matches, the link is verified; status updated to VERIFIED</li>
 * </ol>
 *
 * <p>The National ID is NEVER stored or accepted by this service.</p>
 */
@Service
public class MosipLinkService {

    private static final Logger log = LoggerFactory.getLogger(MosipLinkService.class);
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final MosipLinkRepository mosipRepo;
    private final EventOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final SecretKeySpec kek;
    private final SecureRandom secureRandom;

    public MosipLinkService(MosipLinkRepository mosipRepo,
                             EventOutboxRepository outboxRepo,
                             ObjectMapper objectMapper,
                             IdentityProperties properties) {
        this.mosipRepo = mosipRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.secureRandom = new SecureRandom();

        byte[] keyBytes = Base64.getDecoder().decode(properties.mosipKek());
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "MOSIP KEK must be exactly 32 bytes (256 bits), got " + keyBytes.length);
        }
        this.kek = new SecretKeySpec(keyBytes, "AES");
        log.info("MosipLinkService initialised with AES-256-GCM encryption");
    }

    /**
     * Store an encrypted MOSIP link for a patient.
     *
     * <p>If a link already exists for this (tenant, healthId), it is considered
     * a conflict — each patient should have at most one MOSIP link.</p>
     */
    @Transactional
    public MosipLinkResponse storeLink(MosipLinkRequest request) {
        mosipRepo.findByTenantIdAndHealthId(request.tenantId(), request.healthId())
                .ifPresent(existing -> {
                    throw new IdentityConflictException(
                            "MOSIP link already exists for this Health ID");
                });

        String hash = sha256Hex(request.linkRef());
        byte[] encrypted = encryptAesGcm(request.linkRef());

        MosipLinkEntity entity = new MosipLinkEntity();
        entity.setTenantId(request.tenantId());
        entity.setHealthId(request.healthId());
        entity.setLinkRefHash(hash);
        entity.setLinkRefEncrypted(encrypted);
        entity.setVerificationStatus("PENDING");
        entity.setProofingEventRef(request.proofingEventRef());

        MosipLinkEntity saved = mosipRepo.save(entity);

        publishOutboxEvent(request.tenantId(), "MosipLink", request.healthId().toString(),
                "MOSIP_LINK_CREATED",
                Map.of("tenantId", request.tenantId(),
                       "healthId", request.healthId(),
                       "status", "PENDING"));

        log.info("Stored MOSIP link for tenant={}, healthId={}", request.tenantId(), request.healthId());

        return new MosipLinkResponse(
                saved.getHealthId(),
                saved.getVerificationStatus(),
                saved.getLinkedAt()
        );
    }

    /**
     * Verify a MOSIP link by comparing the SHA-256 hash of the supplied linkRef
     * against the stored hash. If they match, the link is marked as VERIFIED.
     */
    @Transactional
    public MosipVerifyResponse verifyLink(MosipVerifyRequest request) {
        MosipLinkEntity entity = mosipRepo
                .findByTenantIdAndHealthId(request.tenantId(), request.healthId())
                .orElseThrow(() -> new IdentityNotFoundException("MOSIP link not found"));

        String suppliedHash = sha256Hex(request.linkRef());
        boolean verified = suppliedHash.equals(entity.getLinkRefHash());

        if (verified) {
            entity.setVerificationStatus("VERIFIED");
            entity.setLinkedAt(Instant.now());
            mosipRepo.save(entity);

            publishOutboxEvent(request.tenantId(), "MosipLink", request.healthId().toString(),
                    "MOSIP_LINK_VERIFIED",
                    Map.of("tenantId", request.tenantId(),
                           "healthId", request.healthId(),
                           "status", "VERIFIED"));

            log.info("MOSIP link verified for tenant={}, healthId={}",
                    request.tenantId(), request.healthId());
        } else {
            log.warn("MOSIP link verification failed for tenant={}, healthId={}",
                    request.tenantId(), request.healthId());
        }

        return new MosipVerifyResponse(
                entity.getHealthId(),
                verified,
                entity.getVerificationStatus()
        );
    }

    // ── Cryptographic helpers ───────────────────────────────────────────────

    /**
     * Encrypt the link_ref with AES-256-GCM.
     *
     * <p>Output format: [12-byte IV][ciphertext + 16-byte GCM tag]</p>
     */
    private byte[] encryptAesGcm(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, kek, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * Decrypt AES-256-GCM ciphertext produced by {@link #encryptAesGcm(String)}.
     */
    @SuppressWarnings("unused") // Available for future decryption needs
    private String decryptAesGcm(byte[] encryptedData) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, kek, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-256-GCM decryption failed", e);
        }
    }

    /**
     * SHA-256 hash of the input, returned as lowercase hex string.
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 hashing failed", e);
        }
    }

    private void publishOutboxEvent(java.util.UUID tenantId, String aggregateType, String aggregateId,
                                     String eventType, Object payload) {
        // Checked before the try: the catch below swallows failures so a missing tenant would
        // vanish into a log line, and the row would reach the envelope builder with nothing to
        // build from. Every caller has a tenant in scope, so absence here is a coding error.
        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "identity outbox event requires a tenant: " + aggregateType + "/" + eventType);
        }
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setTenantId(tenantId);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
