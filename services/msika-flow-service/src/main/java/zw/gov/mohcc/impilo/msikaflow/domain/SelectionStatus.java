package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * Selection lifecycle (Vol II §11.7 — idempotent commitment, RC-1).
 *
 * <p>{@code AWAITING_PAYMENT} (OF-B10, §8.8): commitment step 8 created a real
 * MusheX payment intent for a positive due-now shortfall and the commitment is
 * held — steps 9–12 run only when the {@code mushex.payment.status.changed}
 * callback flips the intent {@code PAID}. A terminal payment failure or the
 * reservation-TTL timeout compensates (DURA holds + prescription claim
 * released) and fails the selection with a coded outcome.</p>
 */
public enum SelectionStatus {
    SELECTED,
    REVALIDATING,
    AWAITING_PAYMENT,
    COMMITTED,
    FAILED,
    CANCELLED
}
