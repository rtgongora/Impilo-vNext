package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.DeviceProfileEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.DeviceProfileRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.DeviceCacheService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Device risk scoring engine.
 *
 * <p>Computes a risk score (0-100) based on device reputation:
 * <ul>
 *   <li>Unknown device (no fingerprint): configurable score (default 70)</li>
 *   <li>New device (first seen): configurable score (default 50)</li>
 *   <li>Known device: score from device profile history</li>
 *   <li>Blocked device: 100 (immediate deny)</li>
 * </ul>
 * </p>
 *
 * <p>Scores are cached in Redis for fast lookup on every request.</p>
 */
@Component
public class RiskScoring {

    private static final Logger log = LoggerFactory.getLogger(RiskScoring.class);

    private final DeviceCacheService deviceCache;
    private final DeviceProfileRepository deviceRepository;
    private final AuthzProperties properties;

    public RiskScoring(DeviceCacheService deviceCache,
                       DeviceProfileRepository deviceRepository,
                       AuthzProperties properties) {
        this.deviceCache = deviceCache;
        this.deviceRepository = deviceRepository;
        this.properties = properties;
    }

    /**
     * Compute the risk score for a request based on device fingerprint.
     *
     * @param tenantId    tenant isolation
     * @param fingerprint device fingerprint hash (may be null)
     * @param actorId     the requesting actor
     * @return risk score 0-100
     */
    public int score(UUID tenantId, String fingerprint, String actorId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.debug("No device fingerprint provided, risk={}",
                    properties.getRiskThresholds().getUnknownDeviceScore());
            return properties.getRiskThresholds().getUnknownDeviceScore();
        }

        // Check cache first, then DB
        Optional<DeviceProfileEntity> cached = deviceCache.getDeviceProfile(tenantId, fingerprint);

        if (cached.isEmpty()) {
            // First-seen device — create profile with new-device score
            DeviceProfileEntity newDevice = new DeviceProfileEntity();
            newDevice.setTenantId(tenantId);
            newDevice.setFingerprint(fingerprint);
            newDevice.setActorId(actorId);
            newDevice.setRiskScore(properties.getRiskThresholds().getNewDeviceScore());
            newDevice.setBlocked(false);
            newDevice.setLastSeenAt(Instant.now());

            newDevice = deviceRepository.save(newDevice);
            deviceCache.cacheDeviceProfile(tenantId, fingerprint, newDevice);

            log.debug("New device registered: fingerprint={}, risk={}",
                    fingerprint, newDevice.getRiskScore());
            return newDevice.getRiskScore();
        }

        DeviceProfileEntity device = cached.get();

        // Blocked device = maximum risk
        if (device.isBlocked()) {
            log.warn("Blocked device detected: fingerprint={}, tenant={}",
                    fingerprint, tenantId);
            return properties.getRiskThresholds().getBlockedDeviceScore();
        }

        // Update last seen (async-safe: we tolerate slightly stale timestamps)
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
        deviceCache.cacheDeviceProfile(tenantId, fingerprint, device);

        return device.getRiskScore();
    }
}
