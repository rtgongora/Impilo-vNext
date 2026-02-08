package zw.gov.mohcc.impilo.mushex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.OpsReviewEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReviewStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.OpsReviewRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Operations review queue service.
 *
 * Manages a queue of items requiring manual review (fraud flags, high-value
 * transactions, disputed claims, etc.). Reviewers can approve or reject
 * items, recording their decision and notes.
 */
@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);

    private final OpsReviewRepository opsReviewRepository;

    public OpsService(OpsReviewRepository opsReviewRepository) {
        this.opsReviewRepository = opsReviewRepository;
    }

    /**
     * Get all pending review items for a tenant.
     *
     * @param tenantId the tenant ID
     * @param pageable pagination parameters
     * @return page of pending review items
     */
    public Page<OpsReviewEntity> getPendingReviews(UUID tenantId, Pageable pageable) {
        return opsReviewRepository.findByTenantIdAndStatus(tenantId, ReviewStatus.PENDING, pageable);
    }

    /**
     * Approve a review item.
     * Marks the item as APPROVED with the current timestamp and optional notes.
     *
     * @param reviewId the review item ID
     * @param notes    optional approval notes
     * @return the approved review entity
     */
    @Transactional
    public OpsReviewEntity approveReview(String reviewId, String notes) {
        TrustContext ctx = TrustContextHolder.require();

        OpsReviewEntity review = opsReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (review.getStatus() != ReviewStatus.PENDING && review.getStatus() != ReviewStatus.IN_REVIEW) {
            throw new IllegalStateException(
                    "Cannot approve review in status: " + review.getStatus());
        }

        review.setStatus(ReviewStatus.APPROVED);
        review.setAssignedTo(ctx.actorId());
        review.setNotes(notes);
        review.setResolvedAt(OffsetDateTime.now());

        review = opsReviewRepository.save(review);

        log.info("Review approved: reviewId={}, entity={}/{}, approver={}",
                reviewId, review.getEntityType(), review.getEntityId(), ctx.actorId());

        return review;
    }

    /**
     * Reject a review item.
     * Marks the item as REJECTED with the current timestamp and optional notes.
     *
     * @param reviewId the review item ID
     * @param notes    optional rejection notes
     * @return the rejected review entity
     */
    @Transactional
    public OpsReviewEntity rejectReview(String reviewId, String notes) {
        TrustContext ctx = TrustContextHolder.require();

        OpsReviewEntity review = opsReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (review.getStatus() != ReviewStatus.PENDING && review.getStatus() != ReviewStatus.IN_REVIEW) {
            throw new IllegalStateException(
                    "Cannot reject review in status: " + review.getStatus());
        }

        review.setStatus(ReviewStatus.REJECTED);
        review.setAssignedTo(ctx.actorId());
        review.setNotes(notes);
        review.setResolvedAt(OffsetDateTime.now());

        review = opsReviewRepository.save(review);

        log.info("Review rejected: reviewId={}, entity={}/{}, reviewer={}",
                reviewId, review.getEntityType(), review.getEntityId(), ctx.actorId());

        return review;
    }
}
