package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.api.dto.AuditEntryView;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PagedAuditView;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);

    private final OpsReviewRepository opsReviewRepository;
    private final VendorService vendorService;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public OpsService(OpsReviewRepository opsReviewRepository, VendorService vendorService,
                      EventOutboxRepository eventOutboxRepository, ObjectMapper objectMapper) {
        this.opsReviewRepository = opsReviewRepository;
        this.vendorService = vendorService;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
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

    /**
     * Rejecting a vendor-application review must also flip the vendor itself to
     * REJECTED — previously the review queue item moved but the vendor profile stayed
     * APPLIED/PENDING_VERIFICATION forever, so BFF vendor-status views never reflected
     * the ops decision.
     */
    @Transactional
    public OpsReviewEntity rejectReview(String reviewId, String actorId, String notes) {
        OpsReviewEntity review = opsReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        review.setStatus(ReviewStatus.REJECTED);
        review.setAssignedTo(actorId);
        review.setNotes(notes);
        opsReviewRepository.save(review);

        if ("VENDOR".equals(review.getEntityType())) {
            vendorService.rejectVendor(review.getEntityId(), actorId, review.getTenantId(), notes);
        }

        log.info("Review rejected: id={} notes={}", reviewId, notes);
        return review;
    }

    @Transactional
    public VendorProfileEntity suspendVendor(String vendorId, String reason, String actorId, UUID tenantId) {
        return vendorService.suspendVendor(vendorId, reason, actorId, tenantId);
    }

    @Transactional
    public VendorProfileEntity reinstateVendor(String vendorId, String actorId, UUID tenantId) {
        return vendorService.reinstateVendor(vendorId, actorId, tenantId);
    }

    /**
     * Thin read-only union of ops review decisions and published lifecycle events
     * (order/vendor/refund) for a tenant — no new table, just sorted+paged in memory
     * over the two existing sources.
     */
    public PagedAuditView getAuditLog(UUID tenantId, Pageable pageable) {
        // Pull a generously-sized recent window from each source, merge, sort, page.
        Pageable reviewWindow = org.springframework.data.domain.PageRequest.of(0, 200);
        Pageable eventWindow = org.springframework.data.domain.PageRequest.of(0, 200);

        List<AuditEntryView> entries = new ArrayList<>();
        for (OpsReviewEntity r : opsReviewRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId, reviewWindow)) {
            entries.add(new AuditEntryView(
                    r.getId(), "REVIEW", r.getEntityType(), r.getEntityId(),
                    "Review " + r.getStatus() + (r.getNotes() != null ? " — " + r.getNotes() : ""),
                    r.getAssignedTo(), r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt()));
        }
        for (EventOutboxEntity e : eventOutboxRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, eventWindow)) {
            entries.add(new AuditEntryView(
                    String.valueOf(e.getId()), "EVENT", e.getAggregateType(), e.getAggregateId(),
                    e.getEventType(), actorFromPayload(e.getPayload()), e.getCreatedAt()));
        }

        entries.sort(Comparator.comparing(AuditEntryView::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int start = Math.min((int) pageable.getOffset(), entries.size());
        int end = Math.min(start + pageable.getPageSize(), entries.size());
        List<AuditEntryView> page = entries.subList(start, end);
        return new PagedAuditView(page, pageable.getPageNumber(), pageable.getPageSize(), entries.size());
    }

    private String actorFromPayload(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(payload);
            for (String field : new String[] {"actorId", "approvedBy", "suspendedBy", "reinstatedBy", "boundBy"}) {
                if (node.hasNonNull(field)) return node.get(field).asText();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
