package zw.gov.mohcc.impilo.msikaflow.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.util.UUID;

@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);

    private final OpsReviewRepository opsReviewRepository;
    private final VendorService vendorService;

    public OpsService(OpsReviewRepository opsReviewRepository, VendorService vendorService) {
        this.opsReviewRepository = opsReviewRepository;
        this.vendorService = vendorService;
    }

    public Page<OpsReviewEntity> getPendingReviews(UUID tenantId, Pageable pageable) {
        return opsReviewRepository.findByStatusAndTenantId(ReviewStatus.PENDING, tenantId, pageable);
    }

    @Transactional
    public OpsReviewEntity approveReview(String reviewId, String actorId, UUID tenantId) {
        OpsReviewEntity review = opsReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        review.setStatus(ReviewStatus.APPROVED);
        review.setAssignedTo(actorId);
        opsReviewRepository.save(review);

        // If it's a vendor review, approve the vendor
        if ("VENDOR".equals(review.getEntityType())) {
            vendorService.approveVendor(review.getEntityId(), actorId, tenantId);
        }

        log.info("Review approved: id={} entity={}/{}", reviewId, review.getEntityType(), review.getEntityId());
        return review;
    }

    @Transactional
    public OpsReviewEntity rejectReview(String reviewId, String actorId, String notes) {
        OpsReviewEntity review = opsReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        review.setStatus(ReviewStatus.REJECTED);
        review.setAssignedTo(actorId);
        review.setNotes(notes);
        opsReviewRepository.save(review);

        log.info("Review rejected: id={} notes={}", reviewId, notes);
        return review;
    }
}
