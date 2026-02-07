package zw.gov.mohcc.impilo.tshepo.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.persistence.DeviceProfileEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.DeviceProfileRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes a risk score (0–100) for a given request context.
 * Higher score = higher risk. Thresholds:
 *   0-30:  LOW
 *   31-60: MEDIUM
 *   61-80: HIGH (may trigger step-up)
 *   81+:   BLOCKED
 */
@Component
public class RiskScoring {

    private final DeviceProfileRepository deviceRepo;

    public RiskScoring(DeviceProfileRepository deviceRepo) {
        this.deviceRepo = deviceRepo;
    }

    public int score(UUID tenantId, String fingerprint, String actorId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return 70; // Unknown device = high risk
        }

        Optional<DeviceProfileEntity> existing = deviceRepo
            .findByTenantIdAndFingerprint(tenantId, fingerprint);

        if (existing.isEmpty()) {
            // First-seen device: create profile, moderate risk
            DeviceProfileEntity profile = new DeviceProfileEntity();
            profile.setTenantId(tenantId);
            profile.setFingerprint(fingerprint);
            profile.setActorId(actorId);
            profile.setRiskLevel("MEDIUM");
            profile.setFirstSeenAt(Instant.now());
            profile.setLastSeenAt(Instant.now());
            deviceRepo.save(profile);
            return 45;
        }

        DeviceProfileEntity device = existing.get();
        device.setLastSeenAt(Instant.now());
        deviceRepo.save(device);

        return switch (device.getRiskLevel()) {
            case "LOW" -> 10;
            case "MEDIUM" -> 40;
            case "HIGH" -> 70;
            case "BLOCKED" -> 95;
            default -> 50;
        };
    }
}
