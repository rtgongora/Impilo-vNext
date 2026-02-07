package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.KeyRotationLogEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.KeyRotationLogRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Manages key rotation lifecycle: creating new keys, marking old keys as ROTATED,
 * logging rotation events, and publishing outbox events.
 *
 * <p>Supports both manual rotation (via admin API) and automatic scheduled rotation
 * based on configured interval.</p>
 */
@Service
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    private final SigningKeyRepository signingKeyRepository;
    private final KeyRotationLogRepository rotationLogRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final Ed25519SigningService ed25519SigningService;
    private final KeysProperties keysProperties;

    public KeyRotationService(SigningKeyRepository signingKeyRepository,
                               KeyRotationLogRepository rotationLogRepository,
                               EventOutboxRepository eventOutboxRepository,
                               Ed25519SigningService ed25519SigningService,
                               KeysProperties keysProperties) {
        this.signingKeyRepository = signingKeyRepository;
        this.rotationLogRepository = rotationLogRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.ed25519SigningService = ed25519SigningService;
        this.keysProperties = keysProperties;
    }

    /**
     * Manually rotate the active key for a tenant.
     *
     * <p>Creates a new Ed25519 key pair, marks the current active key as ROTATED,
     * logs the rotation event, and writes an outbox event.</p>
     *
     * @param tenantId  the tenant to rotate keys for
     * @param rotatedBy the actor performing the rotation (e.g., admin user ID)
     * @param reason    optional reason for the rotation
     * @return the newly created active signing key
     */
    @Transactional
    public SigningKeyEntity rotateKey(UUID tenantId, String rotatedBy, String reason) {
        // Find current active key(s) for this tenant
        List<SigningKeyEntity> activeKeys = signingKeyRepository.findActiveKeysByTenant(tenantId);

        String oldKeyId = null;

        // Mark all current active keys as ROTATED
        for (SigningKeyEntity activeKey : activeKeys) {
            oldKeyId = activeKey.getKeyId();
            activeKey.setStatus("ROTATED");
            activeKey.setRotatedAt(Instant.now());
            signingKeyRepository.save(activeKey);
            log.info("Marked key {} as ROTATED for tenant {}", activeKey.getKeyId(), tenantId);
        }

        // Generate new key pair
        SigningKeyEntity newKey = ed25519SigningService.generateKeyPair(tenantId);

        // Log the rotation
        KeyRotationLogEntity rotationLog = new KeyRotationLogEntity();
        rotationLog.setTenantId(tenantId);
        rotationLog.setOldKeyId(oldKeyId);
        rotationLog.setNewKeyId(newKey.getKeyId());
        rotationLog.setRotatedBy(rotatedBy);
        rotationLog.setReason(reason);
        rotationLogRepository.save(rotationLog);

        // Write outbox event
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("SigningKey");
        event.setAggregateId(newKey.getKeyId());
        event.setEventType("KEY_ROTATED");
        event.setPayload("{\"tenantId\":\"" + tenantId + "\","
                + "\"oldKeyId\":" + (oldKeyId != null ? "\"" + oldKeyId + "\"" : "null") + ","
                + "\"newKeyId\":\"" + newKey.getKeyId() + "\","
                + "\"rotatedBy\":\"" + rotatedBy + "\","
                + "\"reason\":" + (reason != null ? "\"" + reason + "\"" : "null") + "}");
        eventOutboxRepository.save(event);

        log.info("Key rotation complete for tenant {}: {} -> {}", tenantId, oldKeyId, newKey.getKeyId());
        return newKey;
    }

    /**
     * Deactivate (soft-delete) a specific key by marking it as REVOKED.
     *
     * <p>Revoked keys cannot be used for signing but remain in the database
     * for historical verification purposes.</p>
     */
    @Transactional
    public void revokeKey(String keyId, String revokedBy) {
        SigningKeyEntity key = signingKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

        key.setStatus("REVOKED");
        key.setRotatedAt(Instant.now());
        signingKeyRepository.save(key);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("SigningKey");
        event.setAggregateId(keyId);
        event.setEventType("KEY_REVOKED");
        event.setPayload("{\"keyId\":\"" + keyId + "\","
                + "\"tenantId\":\"" + key.getTenantId() + "\","
                + "\"revokedBy\":\"" + revokedBy + "\"}");
        eventOutboxRepository.save(event);

        log.info("Key {} revoked by {}", keyId, revokedBy);
    }

    /**
     * Scheduled automatic key rotation.
     *
     * <p>Runs daily and rotates any ACTIVE keys that have exceeded the configured
     * rotation interval. This ensures keys are rotated even if no manual rotation
     * is triggered.</p>
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 02:00 UTC
    @Transactional
    public void scheduledRotation() {
        log.info("Running scheduled key rotation check");

        Instant threshold = Instant.now().minus(keysProperties.getRotationIntervalDays(), ChronoUnit.DAYS);
        List<SigningKeyEntity> expiredKeys = signingKeyRepository.findKeysNeedingRotation(threshold);

        if (expiredKeys.isEmpty()) {
            log.info("No keys require rotation");
            return;
        }

        // Group by tenant and rotate
        expiredKeys.stream()
                .map(SigningKeyEntity::getTenantId)
                .distinct()
                .forEach(tenantId -> {
                    try {
                        rotateKey(tenantId, "SYSTEM_SCHEDULER", "Scheduled rotation (interval: "
                                + keysProperties.getRotationIntervalDays() + " days)");
                    } catch (Exception e) {
                        log.error("Failed to rotate key for tenant {}: {}", tenantId, e.getMessage(), e);
                    }
                });
    }
}
