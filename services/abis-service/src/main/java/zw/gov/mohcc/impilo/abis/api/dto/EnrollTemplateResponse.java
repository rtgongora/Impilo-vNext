package zw.gov.mohcc.impilo.abis.api.dto;

/**
 * Enrol result. {@code version} reflects template versioning (re-enrol supersedes prior).
 * {@code duplicateReview} is populated only when enrol-time dedup opened a
 * duplicate-investigation case — the enrol still succeeded (care-first); a human adjudicates,
 * and a merge (if any) is a separate dual-control action. It is NEVER an automatic merge.
 */
public record EnrollTemplateResponse(
        Long id,
        String subjectRef,
        String modality,
        String position,
        String status,
        int version,
        DuplicateReview duplicateReview) {

    /** Signals that a potential-duplicate case was opened for human adjudication. */
    public record DuplicateReview(Long caseId, int candidateCount, Double topScore, String status) {}
}
