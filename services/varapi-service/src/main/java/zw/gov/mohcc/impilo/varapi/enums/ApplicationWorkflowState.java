package zw.gov.mohcc.impilo.varapi.enums;

/**
 * Workflow state for provider applications.
 */
public enum ApplicationWorkflowState {
    DRAFT,
    SUBMITTED,
    UNDER_ADMIN_REVIEW,
    AWAITING_DOCUMENTS,
    AWAITING_VERIFICATION,
    AWAITING_FEE,
    READY_FOR_REVIEW,
    READY_FOR_COMMITTEE,
    DECIDED_APPROVED,
    DECIDED_REJECTED,
    DECIDED_DEFERRED,
    CLOSED_OUT
}