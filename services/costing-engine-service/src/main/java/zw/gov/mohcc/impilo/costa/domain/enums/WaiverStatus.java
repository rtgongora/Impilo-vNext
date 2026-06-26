package zw.gov.mohcc.impilo.costa.domain.enums;

/** Approval lifecycle of a discretionary fee waiver. */
public enum WaiverStatus {
    /** Granted by a clerk/officer, pending approval. */
    GRANTED,
    /** Approved — the waiver is effective. */
    APPROVED,
    /** Rejected at approval. Terminal. */
    REJECTED,
    /** Previously granted/approved, then revoked. Terminal. */
    REVOKED
}
