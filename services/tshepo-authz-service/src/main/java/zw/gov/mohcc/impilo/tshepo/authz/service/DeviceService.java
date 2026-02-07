package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DeviceProfileResponse;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.DeviceProfileEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.DeviceProfileRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for device profile management (block, unblock, query).
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceProfileRepository deviceRepository;
    private final DeviceCacheService deviceCache;

    public DeviceService(DeviceProfileRepository deviceRepository,
                         DeviceCacheService deviceCache) {
        this.deviceRepository = deviceRepository;
        this.deviceCache = deviceCache;
    }

    /**
     * Get a device profile by tenant and fingerprint.
     */
    public DeviceProfileResponse getProfile(UUID tenantId, String fingerprint) {
        DeviceProfileEntity entity = deviceRepository
                .findByTenantIdAndFingerprint(tenantId, fingerprint)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Device not found: " + fingerprint));
        return toResponse(entity);
    }

    /**
     * Block a device (sets blocked=true, risk_score=100).
     */
    @Transactional
    public DeviceProfileResponse blockDevice(UUID tenantId, String fingerprint) {
        DeviceProfileEntity entity = deviceRepository
                .findByTenantIdAndFingerprint(tenantId, fingerprint)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Device not found: " + fingerprint));

        entity.setBlocked(true);
        entity.setRiskScore(100);
        entity.setLastSeenAt(Instant.now());
        entity = deviceRepository.save(entity);

        deviceCache.invalidate(tenantId, fingerprint);

        log.warn("Device BLOCKED: tenant={}, fingerprint={}", tenantId, fingerprint);

        return toResponse(entity);
    }

    /**
     * Unblock a device (sets blocked=false, resets risk_score to 50).
     */
    @Transactional
    public DeviceProfileResponse unblockDevice(UUID tenantId, String fingerprint) {
        DeviceProfileEntity entity = deviceRepository
                .findByTenantIdAndFingerprint(tenantId, fingerprint)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Device not found: " + fingerprint));

        entity.setBlocked(false);
        entity.setRiskScore(50);
        entity.setLastSeenAt(Instant.now());
        entity = deviceRepository.save(entity);

        deviceCache.invalidate(tenantId, fingerprint);

        log.info("Device UNBLOCKED: tenant={}, fingerprint={}", tenantId, fingerprint);

        return toResponse(entity);
    }

    private DeviceProfileResponse toResponse(DeviceProfileEntity entity) {
        return new DeviceProfileResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getFingerprint(),
                entity.getActorId(),
                entity.getRiskScore(),
                entity.isBlocked(),
                entity.getLastSeenAt(),
                entity.getCreatedAt()
        );
    }
}
