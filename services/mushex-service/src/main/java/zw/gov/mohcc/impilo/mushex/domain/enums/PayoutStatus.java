package zw.gov.mohcc.impilo.mushex.domain.enums;

public enum PayoutStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    /** Some items credited, some failed — see the disbursement failures; needs ops attention. */
    PARTIAL,
    FAILED
}
