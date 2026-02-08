package zw.gov.mohcc.impilo.mushex.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service for operations review workflows — manual approvals of
 * flagged transactions, high-value payments, etc.
 */
public interface OpsService {

    /**
     * Get all pending review items for a tenant.
     */
    Page<Object> getPendingReviews(UUID tenantId, Pageable pageable);

    /**
     * Approve a review item, recording the approver and optional notes.
     */
    Object approveReview(String reviewId, String actorId, String notes);
}
