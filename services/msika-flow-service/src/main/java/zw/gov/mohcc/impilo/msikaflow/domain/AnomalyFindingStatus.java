package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * OF-B29 — accountable finding lifecycle (§13.7: findings route to worklists,
 * never fire-and-forget). Transitions: OPEN → REVIEWED, OPEN → DISMISSED,
 * REVIEWED → DISMISSED. DISMISSED is terminal. Review and dismissal are
 * reason-bound (mandatory reviewer + reason).
 */
public enum AnomalyFindingStatus {
    OPEN,
    REVIEWED,
    DISMISSED
}
