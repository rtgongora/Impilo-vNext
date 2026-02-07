package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Lifecycle states of a physical stock count session ({@code inv_count_sessions.status}).
 *
 * <p>A count session moves through a linear workflow from creation to
 * approval (or rejection/cancellation). Once approved, the variance
 * between system and counted quantities is posted as {@code COUNT_ADJUST}
 * ledger events.</p>
 */
public enum CountSessionStatus {

    /** Session created; count lines may be added or edited. */
    DRAFT,

    /** Physical counting is underway. */
    IN_PROGRESS,

    /** Count completed and submitted for supervisor review. */
    SUBMITTED,

    /** Count approved; variance adjustments have been applied to the ledger. */
    APPROVED,

    /** Count rejected by supervisor; requires re-count or correction. */
    REJECTED,

    /** Session cancelled before approval. */
    CANCELLED
}
