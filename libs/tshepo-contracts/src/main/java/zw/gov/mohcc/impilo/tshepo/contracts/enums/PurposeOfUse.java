package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * HL7 / NIST purpose-of-use codes.
 * Every request must declare its purpose; TSHEPO evaluates whether the
 * actor + context + purpose combination is allowed.
 */
public enum PurposeOfUse {
    /** Direct patient care. */
    TREATMENT,
    /** Billing, claims, reimbursement. */
    PAYMENT,
    /** Facility operations, scheduling, queue management. */
    OPERATIONS,
    /** De-identified research data access. */
    RESEARCH,
    /** Surveillance, outbreak management. */
    PUBLIC_HEALTH,
    /** Emergency access — elevated audit, break-glass pre-requisite. */
    EMERGENCY,
    /** Override access — requires step-up + reason + mandatory review. */
    BREAK_GLASS,
    /** Machine-to-machine operations (schedulers, outbox publishers, etc.). */
    SYSTEM
}
