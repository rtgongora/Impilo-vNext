package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * Selection lifecycle (Vol II §11.7 — idempotent commitment, RC-1).
 */
public enum SelectionStatus {
    SELECTED,
    REVALIDATING,
    COMMITTED,
    FAILED,
    CANCELLED
}
