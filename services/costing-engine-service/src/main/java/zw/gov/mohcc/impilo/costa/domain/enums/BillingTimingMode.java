package zw.gov.mohcc.impilo.costa.domain.enums;

/**
 * When billing is evaluated relative to care delivery. Costing may run continuously before,
 * during, and after care; billing timing selects the commercial moment for invoices, claims,
 * and payment handoffs (prospective or retrospective).
 */
public enum BillingTimingMode {
    PRE_SERVICE,
    POINT_OF_CARE,
    POST_SERVICE,
    PERIODIC,
    EPISODE_BASED,
    PACKAGE_BASED,
    CLAIM_BASED,
    DEFERRED
}
