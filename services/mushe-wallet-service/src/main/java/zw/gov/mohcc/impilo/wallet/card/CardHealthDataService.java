package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardHealthDataEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardUpdateQueueEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardHealthDataRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardUpdateQueueRepository;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Manages encrypted health data stored for card synchronization.
 *
 * <p>Health data (critical summaries and IPS bundles) is serialized, compressed,
 * and encrypted before storage. The actual encryption is a placeholder until
 * integration with tshepo-keys-service is complete. Data integrity is verified
 * via SHA-256 hashing of the plaintext.</p>
 *
 * <p>Updates are queued in {@code card_update_queue} for eventual synchronization
 * to the physical smart card via the card-print-agent or NFC writer.</p>
 */
@Service
public class CardHealthDataService {

    private static final Logger log = LoggerFactory.getLogger(CardHealthDataService.class);

    private final CardRepository cardRepository;
    private final CardHealthDataRepository healthDataRepository;
    private final CardUpdateQueueRepository updateQueueRepository;
    private final ObjectMapper objectMapper;

    public CardHealthDataService(CardRepository cardRepository,
                                  CardHealthDataRepository healthDataRepository,
                                  CardUpdateQueueRepository updateQueueRepository,
                                  ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.healthDataRepository = healthDataRepository;
        this.updateQueueRepository = updateQueueRepository;
        this.objectMapper = objectMapper;
    }

    // ── Update Critical Summary ─────────────────────────────────────────

    /**
     * Update the critical health summary for a card.
     *
     * <p>Process: JSON -> CBOR (simulated as JSON) -> compress (gzip) ->
     * encrypt (placeholder) -> store with SHA-256 hash -> queue for card sync.</p>
     *
     * @param cardId           the card to update
     * @param patientCpid      the patient's CPID (no PII — per doctrine)
     * @param summaryJson      the critical summary as JSON string
     * @param encryptionKeyRef reference to the encryption key in tshepo-keys-service
     * @return the stored health data entity
     */
    @Transactional
    public CardHealthDataEntity updateCriticalSummary(UUID cardId, String patientCpid,
                                                       String summaryJson, String encryptionKeyRef,
                                                       UUID tenantId) {
        return updateHealthData(cardId, patientCpid, summaryJson, encryptionKeyRef,
                "CRITICAL_SUMMARY", "HEALTH_SUMMARY", tenantId);
    }

    // ── Update IPS Bundle ───────────────────────────────────────────────

    /**
     * Update the IPS (International Patient Summary) bundle for a card.
     *
     * @param cardId           the card to update
     * @param patientCpid      the patient's CPID
     * @param ipsBundleJson    the IPS FHIR Bundle as JSON string
     * @param encryptionKeyRef reference to the encryption key in tshepo-keys-service
     * @return the stored health data entity
     */
    @Transactional
    public CardHealthDataEntity updateIpsBundle(UUID cardId, String patientCpid,
                                                 String ipsBundleJson, String encryptionKeyRef,
                                                 UUID tenantId) {
        return updateHealthData(cardId, patientCpid, ipsBundleJson, encryptionKeyRef,
                "IPS_BUNDLE", "IPS_BUNDLE", tenantId);
    }

    // ── Read Operations ─────────────────────────────────────────────────

    /**
     * Get health data entity for a card by data type.
     */
    @Transactional(readOnly = true)
    public CardHealthDataEntity getCardHealthData(UUID cardId, String dataType) {
        return healthDataRepository.findByCardIdAndDataType(cardId, dataType)
                .orElse(null);
    }

    /**
     * Get all health data entries for a card.
     */
    @Transactional(readOnly = true)
    public List<CardHealthDataEntity> getAllCardHealthData(UUID cardId) {
        return healthDataRepository.findByCardId(cardId);
    }

    /**
     * Get pending (not yet synced to physical card) updates for a card.
     */
    @Transactional(readOnly = true)
    public List<CardUpdateQueueEntity> getPendingUpdates(UUID cardId) {
        return updateQueueRepository.findByCardIdAndStatus(cardId, "PENDING");
    }

    // ── Mark Synced ─────────────────────────────────────────────────────

    /**
     * Mark a queue entry as COMPLETED (successfully synced to card).
     * Updates the corresponding timestamp on the card entity.
     */
    @Transactional
    public void markSyncedToCard(UUID queueId) {
        CardUpdateQueueEntity queueEntry = updateQueueRepository.findByQueueId(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue entry not found: " + queueId));

        queueEntry.setStatus("COMPLETED");
        queueEntry.setCompletedAt(OffsetDateTime.now());
        updateQueueRepository.save(queueEntry);

        // Update the card's sync timestamps
        CardEntity card = cardRepository.findByCardId(queueEntry.getCardId())
                .orElseThrow(() -> new IllegalStateException("Card not found for queue entry: " + queueId));

        OffsetDateTime now = OffsetDateTime.now();
        if ("HEALTH_SUMMARY".equals(queueEntry.getUpdateType())) {
            card.setHealthDataUpdatedAt(now);
        } else if ("IPS_BUNDLE".equals(queueEntry.getUpdateType())) {
            card.setIpsUpdatedAt(now);
        }
        cardRepository.save(card);

        log.info("Marked queue entry as synced queueId={} cardId={} type={}",
                queueId, queueEntry.getCardId(), queueEntry.getUpdateType());
    }

    // ── Internal ────────────────────────────────────────────────────────

    private CardHealthDataEntity updateHealthData(UUID cardId, String patientCpid,
                                                    String jsonContent, String encryptionKeyRef,
                                                    String dataType, String updateType,
                                                    UUID tenantId) {
        // Verify card exists
        cardRepository.findByCardId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

        // Step 1: Compute SHA-256 hash of plaintext for integrity verification
        String contentHash = sha256Hex(jsonContent);

        // Step 2: Simulate CBOR serialization (using JSON bytes for now)
        byte[] cborBytes = jsonContent.getBytes(StandardCharsets.UTF_8);

        // Step 3: Compress with gzip
        byte[] compressed = gzipCompress(cborBytes);

        // Step 4: Simulate encryption (placeholder — real encryption via tshepo-keys-service)
        // In production: AES-256-GCM encryption using key fetched from tshepo-keys-service
        byte[] encrypted = simulateEncrypt(compressed, encryptionKeyRef);

        // Step 5: Upsert card_health_data
        CardHealthDataEntity healthData = healthDataRepository
                .findByCardIdAndDataType(cardId, dataType)
                .orElseGet(CardHealthDataEntity::new);

        healthData.setCardId(cardId);
        healthData.setTenantId(tenantId);
        healthData.setPatientCpid(patientCpid);
        healthData.setDataType(dataType);
        healthData.setContentEncrypted(encrypted);
        healthData.setContentHash(contentHash);
        healthData.setCompression("GZIP");
        healthData.setEncryptionKeyRef(encryptionKeyRef);
        healthData.setSizeBytes(encrypted.length);
        healthData.setVersion(healthData.getVersion() != null ? healthData.getVersion() + 1 : 1);
        healthData.setGeneratedAt(OffsetDateTime.now());
        healthData.setSyncedToCardAt(null); // Reset — needs re-sync

        healthData = healthDataRepository.save(healthData);

        // Step 6: Queue for card sync
        CardUpdateQueueEntity queueEntry = new CardUpdateQueueEntity();
        queueEntry.setQueueId(UUID.randomUUID());
        queueEntry.setTenantId(tenantId);
        queueEntry.setCardId(cardId);
        queueEntry.setUpdateType(updateType);
        queueEntry.setPayloadEncrypted(encrypted);
        queueEntry.setStatus("PENDING");
        queueEntry.setPriority("CRITICAL_SUMMARY".equals(dataType) ? 1 : 5);
        queueEntry.setAttempts(0);
        updateQueueRepository.save(queueEntry);

        log.info("Updated {} for cardId={} version={} sizeBytes={}",
                dataType, cardId, healthData.getVersion(), encrypted.length);

        return healthData;
    }

    /**
     * Compute SHA-256 hex digest of input string.
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Compress bytes using gzip.
     */
    static byte[] gzipCompress(byte[] data) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
            gzip.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Gzip compression failed", e);
        }
    }

    /**
     * Placeholder for AES-256-GCM encryption.
     * In production, this would fetch the key from tshepo-keys-service and perform
     * real encryption. For now, it returns the input unchanged.
     */
    static byte[] simulateEncrypt(byte[] data, String keyRef) {
        // TODO: Replace with real AES-256-GCM encryption via tshepo-keys-service
        // Key reference is stored in card_health_data.encryption_key_ref for decryption
        return data;
    }
}
