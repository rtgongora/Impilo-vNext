package zw.gov.mohcc.impilo.mushex.domain.enums;

public enum IntentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    PAID,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    /** Outstanding balance recorded (simulation / claims-style lifecycle). */
    PARTIALLY_PAID,
    /** Submitted to external payer or switch (simulation). */
    SUBMITTED,
    /** Payer adjudication complete (simulation). */
    ADJUDICATED,
    /** Payer rejected the obligation (simulation). */
    REJECTED
}
