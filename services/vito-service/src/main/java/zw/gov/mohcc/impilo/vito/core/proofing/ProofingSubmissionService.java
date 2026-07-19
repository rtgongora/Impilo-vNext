package zw.gov.mohcc.impilo.vito.core.proofing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.proofing.engine.DocumentEvidence;
import zw.gov.mohcc.impilo.vito.core.proofing.engine.ProofingSignal;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientVerificationReviewRepository;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wraps {@link ProofingOrchestrator} with the review-queue side effect: a remote
 * document+selfie attempt that cannot auto-verify (native-first: no facial/liveness
 * engine yet) is dropped into the existing {@link ClientVerificationReviewEntity} steward
 * queue rather than silently failing — a human officer then raises it to a witnessed
 * outcome. This keeps the orchestrator pure (decision + ledger) and the persistence
 * concern here.
 */
@Service
public class ProofingSubmissionService {

    private final ProofingOrchestrator orchestrator;
    private final ClientVerificationReviewRepository reviewRepository;

    public ProofingSubmissionService(ProofingOrchestrator orchestrator,
                                     ClientVerificationReviewRepository reviewRepository) {
        this.orchestrator = orchestrator;
        this.reviewRepository = reviewRepository;
    }

    /**
     * @param outcome  the orchestrator disposition
     * @param reviewId the opened review id when disposition is REVIEW_REQUIRED, else null
     */
    public record SubmissionResult(ProofingOrchestrator.ProofingDecision outcome, UUID reviewId) {}

    @Transactional
    public SubmissionResult submitDocumentSelfie(UUID tenantId, UUID healthId,
                                                 DocumentEvidence document,
                                                 String selfieRef, String documentFaceRef,
                                                 String livenessCaptureRef, String verifierId) {
        ProofingOrchestrator.ProofingDecision decision = orchestrator.proveDocumentSelfie(
                tenantId, healthId, document, selfieRef, documentFaceRef, livenessCaptureRef, verifierId);

        UUID reviewId = null;
        if (decision.disposition() == ProofingOrchestrator.Disposition.REVIEW_REQUIRED) {
            reviewId = openReview(tenantId, healthId, decision);
        }
        return new SubmissionResult(decision, reviewId);
    }

    private UUID openReview(UUID tenantId, UUID healthId, ProofingOrchestrator.ProofingDecision decision) {
        ClientVerificationReviewEntity review = new ClientVerificationReviewEntity();
        review.setReviewId(UUID.randomUUID());
        review.setTenantId(tenantId);
        review.setClientHealthId(healthId);
        review.setReviewType("IDENTITY_PROOFING");
        review.setStatus("OPEN");
        review.setNotes(decision.reason() + " | signals: " + decision.signals().stream()
                .map(ProofingSignal::result)
                .collect(Collectors.joining(",")));
        return reviewRepository.save(review).getReviewId();
    }
}
