package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.DeviceProfileEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.DeviceProfileRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.DeviceCacheService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the RiskScoring component.
 *
 * <p>Verifies device reputation scoring logic:
 * unknown device, new device, known device, blocked device.</p>
 */
@ExtendWith(MockitoExtension.class)
class RiskScoringTest {

    @Mock private DeviceCacheService deviceCache;
    @Mock private DeviceProfileRepository deviceRepository;

    private AuthzProperties properties;
    private RiskScoring riskScoring;

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACTOR_ID = "actor-123";
    private static final String FINGERPRINT = "fp-sha256-device-abc";

    @BeforeEach
    void setUp() {
        properties = new AuthzProperties();
        AuthzProperties.RiskThresholds thresholds = new AuthzProperties.RiskThresholds();
        thresholds.setUnknownDeviceScore(70);
        thresholds.setNewDeviceScore(50);
        thresholds.setBlockedDeviceScore(100);
        thresholds.setDenyThreshold(81);
        thresholds.setStepUpTrigger(61);
        properties.setRiskThresholds(thresholds);

        riskScoring = new RiskScoring(deviceCache, deviceRepository, properties);
    }

    @Test
    @DisplayName("score: null fingerprint -> returns unknown device score (70)")
    void score_withNullFingerprint_returnsUnknownDeviceScore() {
        int score = riskScoring.score(TENANT_ID, null, ACTOR_ID);

        assertEquals(70, score,
                "Null fingerprint must return unknownDeviceScore");
        verifyNoInteractions(deviceCache);
        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("score: blank fingerprint -> returns unknown device score (70)")
    void score_withBlankFingerprint_returnsUnknownDeviceScore() {
        int score = riskScoring.score(TENANT_ID, "  ", ACTOR_ID);

        assertEquals(70, score,
                "Blank fingerprint must return unknownDeviceScore");
        verifyNoInteractions(deviceCache);
    }

    @Test
    @DisplayName("score: new device (not in cache or DB) -> creates profile and returns new device score (50)")
    void score_withNewDevice_createsProfileAndReturnsNewDeviceScore() {
        when(deviceCache.getDeviceProfile(TENANT_ID, FINGERPRINT))
                .thenReturn(Optional.empty());

        // Simulate repository save returning the entity with the score set
        when(deviceRepository.save(any(DeviceProfileEntity.class)))
                .thenAnswer(invocation -> {
                    DeviceProfileEntity entity = invocation.getArgument(0);
                    entity.setId(1L);
                    return entity;
                });

        int score = riskScoring.score(TENANT_ID, FINGERPRINT, ACTOR_ID);

        assertEquals(50, score,
                "New device must return newDeviceScore (50)");

        // Verify a new profile was persisted
        ArgumentCaptor<DeviceProfileEntity> captor =
                ArgumentCaptor.forClass(DeviceProfileEntity.class);
        verify(deviceRepository).save(captor.capture());
        DeviceProfileEntity saved = captor.getValue();

        assertEquals(TENANT_ID, saved.getTenantId(),
                "New profile must have correct tenant");
        assertEquals(FINGERPRINT, saved.getFingerprint(),
                "New profile must have the fingerprint");
        assertEquals(ACTOR_ID, saved.getActorId(),
                "New profile must have the actor");
        assertEquals(50, saved.getRiskScore(),
                "New profile must have newDeviceScore");
        assertFalse(saved.isBlocked(),
                "New profile must not be blocked");
        assertNotNull(saved.getLastSeenAt(),
                "New profile must have lastSeenAt set");

        // Verify it was cached
        verify(deviceCache).cacheDeviceProfile(eq(TENANT_ID), eq(FINGERPRINT), any());
    }

    @Test
    @DisplayName("score: known device (in cache) -> returns stored risk score")
    void score_withKnownDevice_returnsStoredScore() {
        DeviceProfileEntity device = new DeviceProfileEntity();
        device.setId(42L);
        device.setTenantId(TENANT_ID);
        device.setFingerprint(FINGERPRINT);
        device.setActorId(ACTOR_ID);
        device.setRiskScore(25);
        device.setBlocked(false);
        device.setLastSeenAt(Instant.now().minusSeconds(60));

        when(deviceCache.getDeviceProfile(TENANT_ID, FINGERPRINT))
                .thenReturn(Optional.of(device));
        when(deviceRepository.save(any(DeviceProfileEntity.class)))
                .thenReturn(device);

        int score = riskScoring.score(TENANT_ID, FINGERPRINT, ACTOR_ID);

        assertEquals(25, score,
                "Known device must return its stored risk score");

        // Verify lastSeenAt was updated
        verify(deviceRepository).save(any(DeviceProfileEntity.class));
        verify(deviceCache).cacheDeviceProfile(TENANT_ID, FINGERPRINT, device);
    }

    @Test
    @DisplayName("score: blocked device -> returns blocked device score (100)")
    void score_withBlockedDevice_returnsBlockedScore() {
        DeviceProfileEntity device = new DeviceProfileEntity();
        device.setId(99L);
        device.setTenantId(TENANT_ID);
        device.setFingerprint(FINGERPRINT);
        device.setActorId(ACTOR_ID);
        device.setRiskScore(25); // Stored score is irrelevant when blocked
        device.setBlocked(true);

        when(deviceCache.getDeviceProfile(TENANT_ID, FINGERPRINT))
                .thenReturn(Optional.of(device));

        int score = riskScoring.score(TENANT_ID, FINGERPRINT, ACTOR_ID);

        assertEquals(100, score,
                "Blocked device must return blockedDeviceScore (100)");

        // Blocked device should NOT update lastSeenAt or re-cache
        verify(deviceRepository, never()).save(any());
        verify(deviceCache, never()).cacheDeviceProfile(any(), any(), any());
    }

    @Test
    @DisplayName("score: custom thresholds are respected")
    void score_withCustomThresholds_usesConfiguredValues() {
        properties.getRiskThresholds().setUnknownDeviceScore(90);
        properties.getRiskThresholds().setNewDeviceScore(60);

        // Re-create with new properties
        riskScoring = new RiskScoring(deviceCache, deviceRepository, properties);

        // Unknown device
        int unknownScore = riskScoring.score(TENANT_ID, null, ACTOR_ID);
        assertEquals(90, unknownScore,
                "Custom unknownDeviceScore must be respected");

        // New device
        when(deviceCache.getDeviceProfile(TENANT_ID, FINGERPRINT))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any(DeviceProfileEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int newScore = riskScoring.score(TENANT_ID, FINGERPRINT, ACTOR_ID);
        assertEquals(60, newScore,
                "Custom newDeviceScore must be respected");
    }
}
