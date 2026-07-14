package zw.gov.mohcc.impilo.costa.domain.enums;

public enum ExemptionCategory {
    CHILD,
    ELDERLY,
    MATERNITY,
    INDIGENT,
    PROGRAM_FUNDED,
    STAFF,
    CHRONIC,
    VETERAN,
    DISABILITY,
    /**
     * Recognised health worker seeking care themselves (dual-source
     * recognition: VARAPI licensed provider or Vashandi workforce).
     * Administrative/financial advantage ONLY — never clinical priority.
     * Deliberately distinct from STAFF (a facility's own employees).
     */
    HEALTH_WORKER,
    OTHER
}
