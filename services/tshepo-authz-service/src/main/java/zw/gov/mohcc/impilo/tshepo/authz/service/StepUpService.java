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

import java.time.Instant;
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

    public StepUpService(StepUpChallengeRepository challengeRepository,
                         AuthzProperties properties) {
        this.challengeRepository = challengeRepository;
        this.properties = properties;
    }

    /**
     * Issue a new step-up challenge.
     */
    @Transactional
    public StepUpChallengeResponse issueChallenge(StepUpChallengeRequest request) {
        StepUpChallengeEntity entity = new StepUpChallengeEntity();
        entity.setTenantId(request.tenantId());
        entity.setActorId(request.actorId());
        entity.setChallengeType(request.challengeType());
        entity.setStatus("PENDING");
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(properties.getStepUpWindowSeconds()));

        entity = challengeRepository.save(entity);

        log.info("Step-up challenge issued: id={}, type={}, actor={}",
                entity.getId(), entity.getChallengeType(), entity.getActorId());

        return toResponse(entity);
    }

    /**
     * Verify (complete) a step-up challenge.
     * In a production system, this would validate the actual MFA/biometric response.
     */
    @Transactional
    public StepUpChallengeResponse verifyChallenge(StepUpVerifyRequest request) {
        Optional<StepUpChallengeEntity> opt = challengeRepository.findPendingById(
                request.challengeId(), request.tenantId(), Instant.now());

        if (opt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Challenge not found, expired, or already completed: " + request.challengeId());
        }

        StepUpChallengeEntity entity = opt.get();

        // Verify the actor matches
        if (!entity.getActorId().equals(request.actorId())) {
            throw new SecurityException("Actor mismatch for challenge " + request.challengeId());
        }

        // In production, validate the verification code against the challenge type:
        // - MFA: validate TOTP/SMS code
        // - BIOMETRIC: validate biometric assertion
        // - SUPERVISOR_APPROVAL: validate supervisor's approval token
        // For now, any non-blank code completes the challenge.
        if (request.verificationCode() == null || request.verificationCode().isBlank()) {
            entity.setStatus("FAILED");
            challengeRepository.save(entity);
            throw new IllegalArgumentException("Verification code is required");
        }

        entity.setStatus("COMPLETED");
        entity.setCompletedAt(Instant.now());
        challengeRepository.save(entity);

        log.info("Step-up challenge completed: id={}, actor={}", entity.getId(), entity.getActorId());

        return toResponse(entity);
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
