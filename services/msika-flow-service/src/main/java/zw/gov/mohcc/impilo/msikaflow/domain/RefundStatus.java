package zw.gov.mohcc.impilo.msikaflow.domain;

public enum RefundStatus {
    REQUESTED,
    APPROVED,
    /** Refund accepted by MusheX (real mushex_refund_id stored); awaiting rail execution. */
    PENDING_EXECUTION,
    PROCESSING,
    COMPLETED,
    /** MusheX reported the refund execution failed. */
    FAILED,
    REJECTED
}
