package zw.gov.mohcc.impilo.tshepo.authz.trust;

/**
 * Honest readiness of a trust authority towards operational (e.g. GDHCN) participation.
 * Deliberately conservative language: the top state is PRODUCTION_NOT_READY, not "ready" —
 * Tshepo is being made GDHCN-<i>ready</i>, and no entry claims production conformance here.
 */
public enum TrustReadiness {
    NOT_STARTED,
    ASSESSMENT_BASELINE,
    FOUNDATION_IN_PROGRESS,
    UAT_NOT_STARTED,
    PRODUCTION_NOT_READY
}
