package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.StepUpChallengeRepository;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.StepUpProviders;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.StepUpUnavailableException;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.StepUpVerificationDispatcher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StepUpService with the real verification engine (B1).
 *
 * <p>Uses a real {@link StepUpVerificationDispatcher} wired with controllable test
 * seams: a fixed TOTP secret (so real RFC-6238 verification is exercised), and
 * fail-closed SMS/biometric adapters.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StepUpServiceTest {

    @Mock private StepUpChallengeRepository challengeRepository;
    @Mock private AuditPublisher auditPublisher;

    private AuthzProperties properties;
    private StepUpService stepUpService;

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACTOR_ID = "actor-123";
    /** RFC 6238 test secret. */
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes();

    @BeforeEach
    void setUp() {
        properties = new AuthzProperties();
        properties.setStepUpWindowSeconds(300);
        properties.setMaxStepUpAttempts(5);

        StepUpProviders.TotpSecretProvider totp = (t, a) ->
                ACTOR_ID.equals(a) ? Optional.of(TOTP_SECRET) : Optional.empty();
        StepUpProviders.OtpDeliveryAdapter sms = (t, a, o) -> {
            throw new StepUpUnavailableException("sms not configured");
        };
        StepUpProviders.BiometricAssertionVerifier bio = (t, a, assertion) -> {
            throw new StepUpUnavailableException("biometric not configured");
        };
        StepUpVerificationDispatcher dispatcher = new StepUpVerificationDispatcher(totp, sms, bio);
        stepUpService = new StepUpService(challengeRepository, properties, dispatcher, auditPublisher);

        when(challengeRepository.save(any(StepUpChallengeEntity.class)))
                .thenAnswer(inv -> {
                    StepUpChallengeEntity e = inv.getArgument(0);
                    if (e.getId() == null) e.setId(UUID.randomUUID());
                    return e;
                });
    }

    private StepUpChallengeEntity pending(String actor, String mode) {
        StepUpChallengeEntity e = new StepUpChallengeEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT_ID);
        e.setActorId(actor);
        e.setChallengeType(mode);
        e.setMode(mode);
        e.setStatus("PENDING");
        e.setIssuedAt(Instant.now().minusSeconds(30));
        e.setExpiresAt(Instant.now().plusSeconds(270));
        return e;
    }

    /** Compute a currently-valid RFC 6238 TOTP code for the test secret. */
    private static String currentTotp() throws Exception {
        long counter = Instant.now().getEpochSecond() / 30;
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(TOTP_SECRET, "HmacSHA1"));
        byte[] h = mac.doFinal(msg);
        int off = h[h.length - 1] & 0xF;
        int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16)
                | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
        return String.format("%06d", bin % 1_000_000);
    }

    // ── issue ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("issueChallenge: TOTP (MFA) creates a PENDING challenge with mode set")
    void issueChallenge_totp_pending() {
        StepUpChallengeResponse r = stepUpService.issueChallenge(
                new StepUpChallengeRequest(TENANT_ID, ACTOR_ID, "MFA"));
        ArgumentCaptor<StepUpChallengeEntity> c = ArgumentCaptor.forClass(StepUpChallengeEntity.class);
        verify(challengeRepository).save(c.capture());
        assertEquals("PENDING", c.getValue().getStatus());
        assertEquals("TOTP", c.getValue().getMode());
        assertEquals("PENDING", r.status());
    }

    @Test
    @DisplayName("issueChallenge: SMS_OTP fails closed when delivery is unconfigured")
    void issueChallenge_smsOtp_failsClosed() {
        assertThrows(StepUpUnavailableException.class, () -> stepUpService.issueChallenge(
                new StepUpChallengeRequest(TENANT_ID, ACTOR_ID, "SMS_OTP")));
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyChallenge: valid TOTP code completes the challenge")
    void verify_validTotp_completes() throws Exception {
        StepUpChallengeEntity e = pending(ACTOR_ID, "TOTP");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));

        StepUpChallengeResponse r = stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, ACTOR_ID, currentTotp()));

        assertEquals("COMPLETED", r.status());
        assertNotNull(r.completedAt());
    }

    @Test
    @DisplayName("verifyChallenge: wrong TOTP code fails (no any-code bypass)")
    void verify_wrongTotp_fails() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "TOTP");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));

        assertThrows(SecurityException.class, () -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, ACTOR_ID, "000000")));
        assertEquals(1, e.getAttemptCount());
        assertNotEquals("COMPLETED", e.getStatus());
    }

    @Test
    @DisplayName("verifyChallenge: TOTP with no enrolled secret fails closed")
    void verify_totpNoSecret_failsClosed() {
        StepUpChallengeEntity e = pending("actor-without-secret", "TOTP");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));

        assertThrows(StepUpUnavailableException.class, () -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, "actor-without-secret", "123456")));
    }

    @Test
    @DisplayName("verifyChallenge: not found / replay throws")
    void verify_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(challengeRepository.findPendingById(eq(id), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(id, TENANT_ID, ACTOR_ID, "123456")));
    }

    @Test
    @DisplayName("verifyChallenge: actor mismatch throws SecurityException")
    void verify_actorMismatch_throws() {
        StepUpChallengeEntity e = pending("different-actor", "TOTP");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));
        assertThrows(SecurityException.class, () -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, ACTOR_ID, "123456")));
    }

    @Test
    @DisplayName("verifyChallenge: attempt limit exceeded -> FAILED + SecurityException")
    void verify_attemptLimit_failsClosed() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "TOTP");
        e.setAttemptCount(5); // already at max
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));
        assertThrows(SecurityException.class, () -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, ACTOR_ID, "000000")));
        assertEquals("FAILED", e.getStatus());
    }

    // ── supervisor approval (dual control) ────────────────────────────────────

    @Test
    @DisplayName("supervisor approval: authorised supervisor completes the flow")
    void supervisorApproval_authorised_completes() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "SUPERVISOR_APPROVAL");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));

        stepUpService.recordSupervisorApproval(e.getId(), TENANT_ID, "supervisor-1", true);
        assertEquals("supervisor-1", e.getApprovedBy());

        StepUpChallengeResponse r = stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, ACTOR_ID, "approval"));
        assertEquals("COMPLETED", r.status());
    }

    @Test
    @DisplayName("supervisor approval: unauthorised principal is rejected")
    void supervisorApproval_unauthorised_rejected() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "SUPERVISOR_APPROVAL");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));
        assertThrows(SecurityException.class, () ->
                stepUpService.recordSupervisorApproval(e.getId(), TENANT_ID, "supervisor-1", false));
    }

    @Test
    @DisplayName("supervisor approval: cannot self-approve (dual control)")
    void supervisorApproval_selfApproval_rejected() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "SUPERVISOR_APPROVAL");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));
        assertThrows(SecurityException.class, () ->
                stepUpService.recordSupervisorApproval(e.getId(), TENANT_ID, ACTOR_ID, true));
    }

    @Test
    @DisplayName("supervisor approval: not approved -> verify fails")
    void supervisorApproval_notApproved_verifyFails() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "SUPERVISOR_APPROVAL");
        when(challengeRepository.findPendingById(eq(e.getId()), eq(TENANT_ID), any(Instant.class)))
                .thenReturn(Optional.of(e));
        assertThrows(SecurityException.class, () -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(e.getId(), TENANT_ID, ACTOR_ID, "approval")));
    }

    // ── status / recent ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus: expired PENDING -> EXPIRED")
    void getStatus_expired() {
        StepUpChallengeEntity e = pending(ACTOR_ID, "TOTP");
        e.setExpiresAt(Instant.now().minusSeconds(10));
        when(challengeRepository.findById(e.getId())).thenReturn(Optional.of(e));
        assertEquals("EXPIRED", stepUpService.getStatus(e.getId(), TENANT_ID).status());
    }

    @Test
    @DisplayName("hasRecentStepUp: true when a recent completion exists")
    void hasRecentStepUp_true() {
        when(challengeRepository.findRecentlyCompletedByActorId(eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(Optional.of(new StepUpChallengeEntity()));
        assertTrue(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID));
    }
}
