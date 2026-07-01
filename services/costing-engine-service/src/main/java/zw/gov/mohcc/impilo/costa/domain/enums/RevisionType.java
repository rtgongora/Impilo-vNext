package zw.gov.mohcc.impilo.costa.domain.enums;

/** Kind of budget revision. VIREMENT must net to zero across lines. */
public enum RevisionType {
    VIREMENT,
    SUPPLEMENTARY,
    REPROGRAMMING,
    RESCISSION
}
