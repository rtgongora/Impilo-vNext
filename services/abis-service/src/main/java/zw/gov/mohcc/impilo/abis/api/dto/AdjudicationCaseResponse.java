package zw.gov.mohcc.impilo.abis.api.dto;

import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.DuplicateInvestigationEntity;

import java.time.Instant;
import java.util.List;

/**
 * Adjudication case view for the operations console. {@code subjectRef} /
 * {@code candidateSubjectRef} are opaque — never Health IDs.
 */
public record AdjudicationCaseResponse(
        Long id,
        String subjectRef,
        String modality,
        String reason,
        String status,
        String decision,
        int candidateCount,
        Double topScore,
        String assignedReviewer,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        String mergeTargetSubjectRef,
        String dualSignOffBy,
        Instant mergedAt,
        Instant createdAt,
        Instant updatedAt,
        List<Candidate> candidates) {

    public record Candidate(String candidateSubjectRef, double score, String summary, int rank) {}

    public static AdjudicationCaseResponse of(AdjudicationCaseEntity e, boolean includeCandidates) {
        List<Candidate> candidates = includeCandidates
                ? e.getCandidates().stream().map(AdjudicationCaseResponse::candidate).toList()
                : List.of();
        return new AdjudicationCaseResponse(
                e.getId(), e.getSubjectRef(), e.getModality(), e.getReason(), e.getStatus(),
                e.getDecision(), e.getCandidateCount(), e.getTopScore(), e.getAssignedReviewer(),
                e.getDecidedBy(), e.getDecidedAt(), e.getDecisionReason(), e.getMergeTargetSubjectRef(),
                e.getDualSignOffBy(), e.getMergedAt(), e.getCreatedAt(), e.getUpdatedAt(), candidates);
    }

    private static Candidate candidate(DuplicateInvestigationEntity c) {
        return new Candidate(c.getCandidateSubjectRef(), c.getScore(), c.getSummary(), c.getRank());
    }
}
