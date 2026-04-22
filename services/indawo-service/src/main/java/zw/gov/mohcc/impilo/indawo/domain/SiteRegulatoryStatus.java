package zw.gov.mohcc.impilo.indawo.domain;

/**
 * Regulatory posture of a non-facility public health site (premises).
 *
 * <p>Indawo governs non-facility sites; TUSO governs health facilities.</p>
 */
public enum SiteRegulatoryStatus {
    APPLICATION_IN_PROGRESS,
    UNDER_REVIEW,
    PENDING_INSPECTION,
    INSPECTION_IN_PROGRESS,
    PENDING_RECTIFICATION,
    PENDING_DECISION,
    APPROVED,
    LICENSED_ACTIVE,
    RENEWAL_DUE,
    RENEWAL_IN_PROGRESS,
    RESTRICTED,
    SUSPENDED,
    PENDING_CLOSURE,
    CLOSED
}

