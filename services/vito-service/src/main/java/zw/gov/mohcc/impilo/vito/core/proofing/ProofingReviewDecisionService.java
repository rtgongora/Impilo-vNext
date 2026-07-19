package zw.gov.mohcc.impilo.vito.core.proofing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientVerificationReviewRepository;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Closes the proofing loop (Wave G): an authorised officer resolves an open
 * IDENTITY_PROOFING / ID_RECOVERY review, and on approval the chosen <b>witnessed</b>
 * assurance outcome is written into the proofing ledger under the officer's id. This is
 * the only path by which a review-required attempt (native-first: no automated
 * facial/liveness) becomes real assurance — a human, not the request, grants it.
 *
 * <p>Only witnessed / document outcomes may be granted by a review decision; a review
 * cannot mint an authoritative (CRVS) or biometric (ABIS) outcome — those come from the
 * respective external verifications, not an officer's say-so.</p>
 */
@Service
public class ProofingReviewDecisionService {

    /** Outcomes an officer review is permitted to grant. */
    private static final Set<ProofingOutcome> REVIEW_GRANTABLE = EnumSet.of(
            ProofingOutcome.DOCUMENT_VERIFIED,
            ProofingOutcome.REMOTELY_WITNESSED,
            ProofingOutcome.IN_PERSON_WITNESSED,
            ProofingOutcome.RECORD_LINKED);

    private final ClientVerificationReviewRepository reviewRepository;
    private final ProofingService proofingService;

    public ProofingReviewDecisionService(ClientVerificationReviewRepository reviewRepository,
                                         ProofingService proofingService) {
        this.reviewRepository = reviewRepository;
        this.proofingService = proofingService;
    }

    public record DecisionResult(UUID reviewId, String status, String decision,
                                 ProofingOutcome grantedOutcome, int assuranceLevel) {}

    /**
     * @param decision APPROVE or REJECT
     * @param outcome  required when APPROVE — the witnessed outcome to grant (must be review-grantable)
     */
    @Transactional
    public DecisionResult decide(UUID tenantId, UUID reviewId, String reviewer,
                                 String decision, ProofingOutcome outcome, String notes) {
        ClientVerificationReviewEntity review = reviewRepository.findByReviewIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        if (!"OPEN".equalsIgnoreCase(review.getStatus())) {
            throw new IllegalStateException("Review is already resolved: " + review.getStatus());
        }
        boolean approve = "APPROVE".equalsIgnoreCase(decision);

        if (approve) {
            if (outcome == null || !REVIEW_GRANTABLE.contains(outcome)) {
                throw new IllegalArgumentException(
                        "An approval must grant a review-grantable outcome (DOCUMENT_VERIFIED, "
                                + "REMOTELY_WITNESSED, IN_PERSON_WITNESSED, RECORD_LINKED)");
            }
            proofingService.recordEvent(tenantId, review.getClientHealthId(),
                    outcome.ledgerMethod(), outcome.assuranceLevel(), "[]", reviewer,
                    "Proofing review " + review.getReviewType() + " approved: " + outcome.name()
                            + (notes != null ? " — " + notes : ""));
            review.setStatus("APPROVED");
            review.setDecision(outcome.name());
        } else {
            proofingService.recordFailedEvent(tenantId, review.getClientHealthId(),
                    ProofingOutcome.DISPUTED.ledgerMethod(), reviewer,
                    "Proofing review " + review.getReviewType() + " rejected"
                            + (notes != null ? " — " + notes : ""));
            review.setStatus("REJECTED");
            review.setDecision("REJECTED");
        }
        review.setReviewer(reviewer);
        review.setReviewedAt(OffsetDateTime.now());
        if (notes != null) {
            review.setNotes(review.getNotes() + " || decision: " + notes);
        }
        reviewRepository.save(review);

        return new DecisionResult(reviewId, review.getStatus(), review.getDecision(),
                approve ? outcome : null, approve ? outcome.assuranceLevel() : 0);
    }
}
