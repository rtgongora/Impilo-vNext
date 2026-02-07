package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassCreateRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassReviewRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.BreakGlassRequestEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.BreakGlassRequestRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing break-glass emergency access override requests.
 *
 * <p>Break-glass is the last resort when a clinician needs access to
 * patient data but normal authorization rules would deny it. Every
 * break-glass request requires:
 * <ul>
 *   <li>A mandatory reason text</li>
 *   <li>A completed step-up challenge</li>
 *   <li>Elevated audit logging</li>
 *   <li>Mandatory post-hoc review by a supervisor</li>
 * </ul>
 * </p>
 *
 * <p>Break-glass requests have a configurable TTL (default 60 minutes)
 * and enter a PENDING_REVIEW state that must be resolved within the
 * review SLA (default 24 hours).</p>
 */
@Service
public class BreakGlassService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassService.class);

    private final BreakGlassRequestRepository breakGlassRepository;
    private final AuthzProperties properties;

    public BreakGlassService(BreakGlassRequestRepository breakGlassRepository,
                             AuthzProperties properties) {
        this.breakGlassRepository = breakGlassRepository;
        this.properties = properties;
    }

    /**
     * Create a break-glass emergency access request.
     */
    @Transactional
    public BreakGlassResponse createRequest(BreakGlassCreateRequest request) {
        BreakGlassRequestEntity entity = new BreakGlassRequestEntity();
        entity.setTenantId(request.tenantId());
        entity.setActorId(request.actorId());
        entity.setReason(request.reason());
        entity.setResourceType(request.resourceType());
        entity.setResourceId(request.resourceId());
        entity.setReviewStatus("PENDING_REVIEW");
        entity.setGrantedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(
                (long) properties.getBreakGlassTtlMinutes() * 60));

        entity = breakGlassRepository.save(entity);

        log.warn("BREAK-GLASS REQUEST created: id={}, actor={}, reason='{}'",
                entity.getId(), entity.getActorId(), entity.getReason());

        return toResponse(entity);
    }

    /**
     * Check if the actor has an active (non-expired) break-glass request.
     */
    public boolean hasActiveBreakGlass(UUID tenantId, String actorId) {
        List<BreakGlassRequestEntity> active =
                breakGlassRepository.findActiveByTenantIdAndActorId(
                        tenantId, actorId, Instant.now());
        return !active.isEmpty();
    }

    /**
     * Get all pending-review break-glass requests for a tenant.
     */
    public List<BreakGlassResponse> getPendingReviews(UUID tenantId) {
        return breakGlassRepository
                .findByTenantIdAndReviewStatusOrderByGrantedAtDesc(tenantId, "PENDING_REVIEW")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Approve or reject a break-glass request.
     */
    @Transactional
    public BreakGlassResponse reviewRequest(UUID id, BreakGlassReviewRequest request) {
        BreakGlassRequestEntity entity = breakGlassRepository
                .findByIdAndTenantId(id, request.tenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Break-glass request not found: " + id));

        if (!"PENDING_REVIEW".equals(entity.getReviewStatus())) {
            throw new IllegalStateException(
                    "Break-glass request already reviewed: " + entity.getReviewStatus());
        }

        entity.setApproved(request.approved());
        entity.setReviewStatus(request.approved() ? "APPROVED" : "REJECTED");
        entity.setReviewedBy(request.reviewerId());
        entity.setReviewedAt(Instant.now());

        entity = breakGlassRepository.save(entity);

        log.warn("BREAK-GLASS REVIEW: id={}, verdict={}, reviewer={}",
                entity.getId(), entity.getReviewStatus(), entity.getReviewedBy());

        return toResponse(entity);
    }

    private BreakGlassResponse toResponse(BreakGlassRequestEntity entity) {
        return new BreakGlassResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getReason(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getApproved(),
                entity.getReviewStatus(),
                entity.getGrantedAt(),
                entity.getExpiresAt(),
                entity.getReviewedBy(),
                entity.getReviewedAt()
        );
    }
}
