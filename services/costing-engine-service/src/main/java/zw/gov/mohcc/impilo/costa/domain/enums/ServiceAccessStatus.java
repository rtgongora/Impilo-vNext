package zw.gov.mohcc.impilo.costa.domain.enums;

/** Outcome of a pre-service (or gate) access decision relative to payment and coverage. */
public enum ServiceAccessStatus {
    ALLOWED_WITHOUT_PAYMENT,
    PAYMENT_REQUIRED_BEFORE_SERVICE,
    DEPOSIT_REQUIRED,
    AUTHORISATION_REQUIRED,
    COVERED_BY_PAYER,
    EXEMPT,
    WAIVER_REQUIRED,
    DEFERRED_PAYMENT_ALLOWED,
    BLOCKED_PENDING_PAYMENT,
    BLOCKED_PENDING_AUTHORISATION,
    EMERGENCY_OVERRIDE
}
