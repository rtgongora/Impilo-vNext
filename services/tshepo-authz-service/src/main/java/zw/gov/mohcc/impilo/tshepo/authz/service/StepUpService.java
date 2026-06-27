package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.StepUpChallengeRepository;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.StepUpMode;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.StepUpUnavailableException;
import zw.gov.mohcc.impilo.tshepo.authz.stepup.StepUpVerificationDispatcher;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing step-up authentication challenges.
 *
 * <p>Step-up challenges are issued when the PolicyEngine determines that
 * a higher level of assurance is required. Challenge types include MFA,
 * BIOMETRIC, and SUPERVISOR_APPROVAL.</p>
 *
 * <p>Challenges expire after the configured window (default 300 seconds).</p>
 */
@Service
public class StepUpService {

    private static final Logger log = LoggerFactory.getLogger(StepUpService.class);

    private final StepUpChallengeRepository challengeRepository;
    private final AuthzProperties properties;
    private final StepUpVerificationDispatcher dispatcher;
    private final AuditPublisher auditPublisher;

    public StepUpService(StepUpChallengeRepository challengeRepository,
                         AuthzProperties properties,
                         StepUpVerificationDispatcher dispatcher,
                         AuditPublisher auditPublisher) {
        this.challengeRepository = challengeRepository;
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Issue a new step-up challenge.
     */
    @Transactional
    public StepUpChallengeResponse issueChallenge(StepUpChallengeRequest request) {
        StepUpMode mode = StepUpMode.resolve(request.challengeType());
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setTenantId(request.tenantId());
        entity.setActorId(request.actorId());
        entity.setChallengeType(request.challengeType());
        entity.setMode(mode.name());
        entity.setStatus("PENDING");
        entity.setAttemptCount(0);
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(properties.getStepUpWindowSeconds()));

        // Mode-specific issue preparation. SMS_OTP generates + stores (hashed) + delivers
        // the OTP here; if its provider is unconfigured this throws and the challenge is
        // NOT issued (fail closed) — never an undeliverable/unverifiable challenge.
        dispatcher.require(mode).onIssue(entity);

        entity = challengeRepository.save(entity);

        auditPublisher.queueGovernanceEvent("STEP_UP_ISSUED", entity.getId().toString(), Map.of(
                "tenantId", entity.getTenantId().toString(),
                "actorId", entity.getActorId(),
                "mode", mode.name()));
        log.info("Step-up challenge issued: id={}, mode={}, actor={}",
                entity.getId(), mode, entity.getActorId());

        return toResponse(entity);
    }

    /**
     * Verify (complete) a step-up challenge. Verification is delegated to the
     * mode-specific {@link StepUpVerifier}; there is no "any code passes" path.
     * Replays (non-pending challenges) are rejected by the pending-only lookup,
     * attempts are counted and capped, and every outcome is audited.
     *
     * <p>Rejection is signalled by throwing. Those throws must NOT roll back the
     * transaction, or the attempt-count increment (which drives lockout) and the
     * {@code STEP_UP_REJECTED} audit would be reverted on every failed attempt —
     * defeating the lockout entirely and losing the rejection audit trail. Hence the
     * deliberate {@code noRollbackFor} on the exceptions this method throws on reject.</p>
     */
    @Transactional(noRollbackFor = {
            SecurityException.class,
            IllegalArgumentException.class,
            StepUpUnavailableException.class
    })
    public StepUpChallengeResponse verifyChallenge(StepUpVerifyRequest request) {
        Optional<StepUpChallengeEntity> opt = challengeRepository.findPendingById(
                request.challengeId(), request.tenantId(), Instant.now());

        if (opt.isEmpty()) {
            // Not found, expired, or already completed/failed (replay) — fail closed.
            auditPublisher.queueGovernanceEvent("STEP_UP_REJECTED", request.challengeId().toString(),
                    Map.of("reason", "NOT_PENDING_OR_EXPIRED", "actorId", request.actorId()));
            throw new IllegalArgumentException(
                    "Challenge not found, expired, or already completed: " + request.challengeId());
        }

        StepUpChallengeEntity entity = opt.get();

        if (!entity.getActorId().equals(request.actorId())) {
            auditReject(entity, "ACTOR_MISMATCH");
            throw new SecurityException("Actor mismatch for challenge " + request.challengeId());
        }

        entity.setAttemptCount(entity.getAttemptCount() + 1);
        if (entity.getAttemptCount() > properties.getMaxStepUpAttempts()) {
            entity.setStatus("FAILED");
            challengeRepository.save(entity);
            auditReject(entity, "ATTEMPTS_EXCEEDED");
            throw new SecurityException("Step-up attempt limit exceeded for challenge " + request.challengeId());
        }

        StepUpMode mode = StepUpMode.resolve(
                entity.getMode() != null ? entity.getMode() : entity.getChallengeType());
        boolean verified;
        try {
            verified = dispatcher.require(mode).verify(entity, request.verificationCode());
        } catch (StepUpUnavailableException e) {
            challengeRepository.save(entity); // persist the attempt
            auditReject(entity, "PROVIDER_UNAVAILABLE");
            throw e; // fail closed — provider not configured
        }

        if (!verified) {
            challengeRepository.save(entity); // persist the attempt
            auditReject(entity, "INVALID_CREDENTIAL");
            throw new SecurityException("Step-up verification failed for challenge " + request.challengeId());
        }

        entity.setStatus("COMPLETED");
        entity.setCompletedAt(Instant.now());
        challengeRepository.save(entity);
        auditPublisher.queueGovernanceEvent("STEP_UP_COMPLETED", entity.getId().toString(),
                Map.of("actorId", entity.getActorId(), "mode", mode.name()));
        log.info("Step-up challenge completed: id={}, mode={}, actor={}", entity.getId(), mode, entity.getActorId());

        return toResponse(entity);
    }

    /**
     * Record a supervisor's approval of a SUPERVISOR_APPROVAL challenge (dual control).
     * The supervisor must be an authorised supervisor principal (verified server-side by
     * the caller from trust context, not a client-supplied name) and must not be the
     * challenge actor. After approval, the actor completes the challenge via verify.
     */
    @Transactional
    public void recordSupervisorApproval(UUID challengeId, UUID tenantId,
                                         String supervisorActorId, boolean authorisedSupervisor) {
        StepUpChallengeEntity entity = challengeRepository.findPendingById(challengeId, tenantId, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not pending: " + challengeId));
        if (!authorisedSupervisor) {
            throw new SecurityException("Supervisor approval requires an authorised supervisor principal");
        }
        if (supervisorActorId == null || supervisorActorId.isBlank()) {
            throw new SecurityException("Supervisor principal required");
        }
        if (supervisorActorId.equals(entity.getActorId())) {
            throw new SecurityException("Dual control: a supervisor cannot approve their own step-up");
        }
        entity.setApprovedBy(supervisorActorId);
        challengeRepository.save(entity);
        auditPublisher.queueGovernanceEvent("STEP_UP_SUPERVISOR_APPROVED", challengeId.toString(),
                Map.of("supervisor", supervisorActorId, "actorId", entity.getActorId()));
    }

    private void auditReject(StepUpChallengeEntity entity, String reason) {
        auditPublisher.queueGovernanceEvent("STEP_UP_REJECTED", entity.getId().toString(),
                Map.of("reason", reason, "actorId", entity.getActorId(),
                        "attemptCount", entity.getAttemptCount()));
    }

    /**
     * Get the current status of a step-up challenge.
     */
    public StepUpChallengeResponse getStatus(UUID challengeId, UUID tenantId) {
        StepUpChallengeEntity entity = challengeRepository.findById(challengeId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Challenge not found: " + challengeId));

        // Check if expired
        if ("PENDING".equals(entity.getStatus()) && entity.getExpiresAt().isBefore(Instant.now())) {
            entity.setStatus("EXPIRED");
            challengeRepository.save(entity);
        }

        return toResponse(entity);
    }

    /**
     * Check if the actor has a recently completed step-up challenge (within window).
     */
    public boolean hasRecentStepUp(UUID tenantId, String actorId) {
        Instant windowStart = Instant.now().minusSeconds(properties.getStepUpWindowSeconds());
        return challengeRepository.findRecentlyCompletedByActorId(tenantId, actorId, windowStart)
                .isPresent();
    }

    private StepUpChallengeResponse toResponse(StepUpChallengeEntity entity) {
        return new StepUpChallengeResponse(
                entity.getId(),
                entity.getChallengeType(),
                entity.getStatus(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getCompletedAt()
        );
    }
}
