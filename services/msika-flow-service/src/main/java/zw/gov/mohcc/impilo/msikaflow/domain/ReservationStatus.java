package zw.gov.mohcc.impilo.msikaflow.domain;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    RELEASED,
    /** Fulfilled — DURA reported the hold consumed by a ledger ISSUE (OF-B11 projection). */
    CONSUMED,
    EXPIRED
}
