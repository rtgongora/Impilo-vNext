package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.StepUpChallengeRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the StepUpService.
 *
 * <p>Covers challenge issuance, verification, status checks,
 * and recent step-up detection within the configured window.</p>
 */
@ExtendWith(MockitoExtension.class)
class StepUpServiceTest {

    @Mock private StepUpChallengeRepository challengeRepository;

    private AuthzProperties properties;
    private StepUpService stepUpService;

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACTOR_ID = "actor-123";

    @BeforeEach
    void setUp() {
        properties = new AuthzProperties();
        properties.setStepUpWindowSeconds(300); // 5-minute window
        stepUpService = new StepUpService(challengeRepository, properties);
    }

    // ════════════════════════════════════════════════════════════════════
    // issueChallenge
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("issueChallenge: creates PENDING challenge with correct expiry")
    void issueChallenge_createsPendingChallenge() {
        StepUpChallengeRequest request = new StepUpChallengeRequest(
                TENANT_ID, ACTOR_ID, "MFA"
        );

        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(invocation -> {
                    StepUpChallengeEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(UUID.randomUUID());
                    }
                    return entity;
                });

        StepUpChallengeResponse response = stepUpService.issueChallenge(request);

        // Verify the entity saved
        ArgumentCaptor<StepUpChallengeEntity> captor =
                ArgumentCaptor.forClass(StepUpChallengeEntity.class);
        verify(challengeRepository).save(captor.capture());
        StepUpChallengeEntity saved = captor.getValue();

        assertEquals(TENANT_ID, saved.getTenantId(),
                "Challenge must have correct tenant");
        assertEquals(ACTOR_ID, saved.getActorId(),
                "Challenge must have correct actor");
        assertEquals("MFA", saved.getChallengeType(),
                "Challenge must have correct type");
        assertEquals("PENDING", saved.getStatus(),
                "New challenge must be PENDING");
        assertNotNull(saved.getIssuedAt(),
                "issuedAt must be set");
        assertNotNull(saved.getExpiresAt(),
                "expiresAt must be set");

        // Verify response mapping
        assertNotNull(response, "Response must not be null");
        assertEquals("MFA", response.challengeType(),
                "Response must reflect challenge type");
        assertEquals("PENDING", response.status(),
                "Response must have PENDING status");
    }

    @Test
    @DisplayName("issueChallenge: expiresAt = issuedAt + stepUpWindowSeconds")
    void issueChallenge_setsExpiryFromConfig() {
        StepUpChallengeRequest request = new StepUpChallengeRequest(
                TENANT_ID, ACTOR_ID, "BIOMETRIC"
        );

        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(inv -> {
                    StepUpChallengeEntity e = inv.getArgument(0);
                    if (e.getId() == null) e.setId(UUID.randomUUID());
                    return e;
                });

        stepUpService.issueChallenge(request);

        ArgumentCaptor<StepUpChallengeEntity> captor =
                ArgumentCaptor.forClass(StepUpChallengeEntity.class);
        verify(challengeRepository).save(captor.capture());
        StepUpChallengeEntity saved = captor.getValue();

        long windowSeconds = java.time.Duration.between(saved.getIssuedAt(), saved.getExpiresAt()).getSeconds();
        org.junit.jupiter.api.Assertions.assertEquals(
                300L,
                windowSeconds,
                "expiresAt must be issuedAt + stepUpWindowSeconds (300)");
    }

    // ════════════════════════════════════════════════════════════════════
    // verifyChallenge
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("verifyChallenge: valid code -> COMPLETED status with completedAt")
    void verifyChallenge_validCode_completesChallenge() {
        UUID challengeId = UUID.randomUUID();
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setChallengeType("MFA");
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now().minusSeconds(30));
        entity.setExpiresAt(Instant.now().plusSeconds(270));

        when(challengeRepository.findPendingById(eq(challengeId), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(entity));
        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StepUpVerifyRequest verifyRequest = new StepUpVerifyRequest(
                challengeId, TENANT_ID, ACTOR_ID, "123456"
        );

        StepUpChallengeResponse response = stepUpService.verifyChallenge(verifyRequest);

        assertEquals("COMPLETED", response.status(),
                "Verified challenge must be COMPLETED");
        assertNotNull(response.completedAt(),
                "completedAt must be set after verification");

        // Verify entity was saved with COMPLETED
        ArgumentCaptor<StepUpChallengeEntity> captor =
                ArgumentCaptor.forClass(StepUpChallengeEntity.class);
        verify(challengeRepository, atLeastOnce()).save(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("verifyChallenge: challenge not found -> throws IllegalArgumentException")
    void verifyChallenge_notFound_throwsException() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findPendingById(eq(challengeId), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.empty());

        StepUpVerifyRequest request = new StepUpVerifyRequest(
                challengeId, TENANT_ID, ACTOR_ID, "123456"
        );

        assertThrows(IllegalArgumentException.class,
                () -> stepUpService.verifyChallenge(request),
                "Non-existent challenge must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("verifyChallenge: actor mismatch -> throws SecurityException")
    void verifyChallenge_actorMismatch_throwsSecurityException() {
        UUID challengeId = UUID.randomUUID();
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId("different-actor");
        entity.setChallengeType("MFA");
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now().minusSeconds(30));
        entity.setExpiresAt(Instant.now().plusSeconds(270));

        when(challengeRepository.findPendingById(eq(challengeId), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(entity));

        StepUpVerifyRequest request = new StepUpVerifyRequest(
                challengeId, TENANT_ID, ACTOR_ID, "123456"
        );

        assertThrows(SecurityException.class,
                () -> stepUpService.verifyChallenge(request),
                "Actor mismatch must throw SecurityException");
    }

    @Test
    @DisplayName("verifyChallenge: blank verification code -> FAILED status and throws")
    void verifyChallenge_blankCode_failsChallenge() {
        UUID challengeId = UUID.randomUUID();
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setChallengeType("MFA");
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now().minusSeconds(30));
        entity.setExpiresAt(Instant.now().plusSeconds(270));

        when(challengeRepository.findPendingById(eq(challengeId), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(entity));
        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StepUpVerifyRequest request = new StepUpVerifyRequest(
                challengeId, TENANT_ID, ACTOR_ID, "  "
        );

        assertThrows(IllegalArgumentException.class,
                () -> stepUpService.verifyChallenge(request),
                "Blank verification code must throw IllegalArgumentException");

        // Verify the entity was marked FAILED
        ArgumentCaptor<StepUpChallengeEntity> captor =
                ArgumentCaptor.forClass(StepUpChallengeEntity.class);
        verify(challengeRepository).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus(),
                "Challenge with blank code must be marked FAILED");
    }

    @Test
    @DisplayName("verifyChallenge: null verification code -> FAILED status and throws")
    void verifyChallenge_nullCode_failsChallenge() {
        UUID challengeId = UUID.randomUUID();
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setChallengeType("MFA");
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now().minusSeconds(30));
        entity.setExpiresAt(Instant.now().plusSeconds(270));

        when(challengeRepository.findPendingById(eq(challengeId), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(entity));
        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StepUpVerifyRequest request = new StepUpVerifyRequest(
                challengeId, TENANT_ID, ACTOR_ID, null
        );

        assertThrows(IllegalArgumentException.class,
                () -> stepUpService.verifyChallenge(request),
                "Null verification code must throw IllegalArgumentException");
    }

    // ════════════════════════════════════════════════════════════════════
    // getStatus
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getStatus: returns current status for valid challenge")
    void getStatus_validChallenge_returnsStatus() {
        UUID challengeId = UUID.randomUUID();
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setChallengeType("BIOMETRIC");
        entity.setStatus("COMPLETED");
        entity.setIssuedAt(Instant.now().minusSeconds(120));
        entity.setExpiresAt(Instant.now().plusSeconds(180));
        entity.setCompletedAt(Instant.now().minusSeconds(60));

        when(challengeRepository.findById(challengeId))
                .thenReturn(Optional.of(entity));

        StepUpChallengeResponse response = stepUpService.getStatus(challengeId, TENANT_ID);

        assertEquals("COMPLETED", response.status(),
                "Must return current status");
        assertEquals("BIOMETRIC", response.challengeType(),
                "Must return correct challenge type");
        assertNotNull(response.completedAt(),
                "completedAt must be set for completed challenge");
    }

    @Test
    @DisplayName("getStatus: expired PENDING challenge -> status auto-updated to EXPIRED")
    void getStatus_expiredPendingChallenge_updatesToExpired() {
        UUID challengeId = UUID.randomUUID();
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setChallengeType("MFA");
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now().minusSeconds(600));
        entity.setExpiresAt(Instant.now().minusSeconds(300)); // Already expired

        when(challengeRepository.findById(challengeId))
                .thenReturn(Optional.of(entity));
        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StepUpChallengeResponse response = stepUpService.getStatus(challengeId, TENANT_ID);

        assertEquals("EXPIRED", response.status(),
                "Expired PENDING challenge must be auto-updated to EXPIRED");

        // Verify the entity was saved with EXPIRED status
        ArgumentCaptor<StepUpChallengeEntity> captor =
                ArgumentCaptor.forClass(StepUpChallengeEntity.class);
        verify(challengeRepository).save(captor.capture());
        assertEquals("EXPIRED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("getStatus: challenge not found -> throws IllegalArgumentException")
    void getStatus_notFound_throwsException() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> stepUpService.getStatus(challengeId, TENANT_ID),
                "Non-existent challenge must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("getStatus: challenge belongs to different tenant -> throws IllegalArgumentException")
    void getStatus_wrongTenant_throwsException() {
        UUID challengeId = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(challengeId);
        entity.setTenantId(otherTenant); // Different tenant
        entity.setActorId(ACTOR_ID);
        entity.setChallengeType("MFA");
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(300));

        when(challengeRepository.findById(challengeId))
                .thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class,
                () -> stepUpService.getStatus(challengeId, TENANT_ID),
                "Challenge from different tenant must throw IllegalArgumentException");
    }

    // ════════════════════════════════════════════════════════════════════
    // hasRecentStepUp
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("hasRecentStepUp: recently completed challenge -> returns true")
    void hasRecentStepUp_recentlyCompleted_returnsTrue() {
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setStatus("COMPLETED");
        entity.setCompletedAt(Instant.now().minusSeconds(60)); // Completed 1 min ago

        when(challengeRepository.findRecentlyCompletedByActorId(
                eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(Optional.of(entity));

        assertTrue(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID),
                "Must return true when recently completed step-up exists");
    }

    @Test
    @DisplayName("hasRecentStepUp: no recent completion -> returns false")
    void hasRecentStepUp_noRecentCompletion_returnsFalse() {
        when(challengeRepository.findRecentlyCompletedByActorId(
                eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertFalse(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID),
                "Must return false when no recently completed step-up exists");
    }

    @Test
    @DisplayName("hasRecentStepUp: window start is computed from properties")
    void hasRecentStepUp_usesConfiguredWindow() {
        // With 300-second window, windowStart should be ~now - 300s
        when(challengeRepository.findRecentlyCompletedByActorId(
                eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(Optional.empty());

        Instant before = Instant.now().minusSeconds(300);

        stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID);

        ArgumentCaptor<Instant> windowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(challengeRepository).findRecentlyCompletedByActorId(
                eq(TENANT_ID), eq(ACTOR_ID), windowCaptor.capture());

        Instant capturedWindow = windowCaptor.getValue();
        Instant after = Instant.now().minusSeconds(300);

        // The window start should be approximately now - 300 seconds
        // Allow 2 seconds of tolerance for test execution time
        assertTrue(capturedWindow.isAfter(before.minusSeconds(2)),
                "Window start must be approximately now - stepUpWindowSeconds");
        assertTrue(capturedWindow.isBefore(after.plusSeconds(2)),
                "Window start must be approximately now - stepUpWindowSeconds");
    }
}
