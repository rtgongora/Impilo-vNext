package zw.gov.mohcc.impilo.costa.domain.enums;

/**
 * Lifecycle FSM for a managed budget (and its current version).
 * Transitions are governed centrally in {@code BudgetLifecycleService.transition}.
 */
public enum BudgetStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    ACTIVE,
    REVISED,
    FROZEN,
    CLOSED,
    ARCHIVED
}
