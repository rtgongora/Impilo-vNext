package zw.gov.mohcc.impilo.varapi.enums;

/**
 * Lifecycle of a self-service provider-access request (IATG Journey D).
 *
 * <p>A request is a durable, applicant-queryable record. New Provider IDs are
 * never auto-issued from a self-service submission: an unmatched request lands
 * in a PENDING_* state naming the next responsible actor, and reaches a terminal
 * decision only through that actor (or adjudication). This is the "no data entry
 * going nowhere" contract — every submission persists and has a next step.</p>
 */
public enum ProviderAccessRequestStatus {
    SUBMITTED,
    PENDING_COUNCIL_REVIEW,
    PENDING_EMPLOYER_REVIEW,
    PENDING_ORGANIZATION_REVIEW,
    PENDING_NATIONAL_REVIEW,
    NEEDS_MORE_INFORMATION,
    NEEDS_ADJUDICATION,
    DUPLICATE_SUSPECTED,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
