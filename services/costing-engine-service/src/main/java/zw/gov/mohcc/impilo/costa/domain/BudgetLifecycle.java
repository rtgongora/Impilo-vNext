package zw.gov.mohcc.impilo.costa.domain;

import zw.gov.mohcc.impilo.costa.domain.enums.BudgetApprovalAction;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;

import java.util.Map;

/**
 * Central budget lifecycle FSM. The only place transitions are defined, so
 * every service path (and the audit log) agrees on legality.
 *
 * <pre>
 * DRAFT --SUBMIT--> SUBMITTED --REVIEW--> UNDER_REVIEW --APPROVE--> APPROVED --ACTIVATE--> ACTIVE
 *   ^                    |                     |                                              |
 *   +----RETURN----------+       REJECT/RETURN-+                              FREEZE <-> ACTIVATE
 *                                                                                            |
 * ACTIVE/FROZEN --CLOSE--> CLOSED --ARCHIVE--> ARCHIVED
 * </pre>
 */
public final class BudgetLifecycle {

    private BudgetLifecycle() {}

    private static String key(BudgetStatus from, BudgetApprovalAction action) {
        return from.name() + ":" + action.name();
    }

    private static final Map<String, BudgetStatus> TRANSITIONS = Map.ofEntries(
            Map.entry(key(BudgetStatus.DRAFT, BudgetApprovalAction.SUBMIT), BudgetStatus.SUBMITTED),
            Map.entry(key(BudgetStatus.SUBMITTED, BudgetApprovalAction.REVIEW), BudgetStatus.UNDER_REVIEW),
            Map.entry(key(BudgetStatus.SUBMITTED, BudgetApprovalAction.RETURN), BudgetStatus.DRAFT),
            Map.entry(key(BudgetStatus.UNDER_REVIEW, BudgetApprovalAction.APPROVE), BudgetStatus.APPROVED),
            Map.entry(key(BudgetStatus.UNDER_REVIEW, BudgetApprovalAction.REJECT), BudgetStatus.DRAFT),
            Map.entry(key(BudgetStatus.UNDER_REVIEW, BudgetApprovalAction.RETURN), BudgetStatus.DRAFT),
            Map.entry(key(BudgetStatus.APPROVED, BudgetApprovalAction.ACTIVATE), BudgetStatus.ACTIVE),
            Map.entry(key(BudgetStatus.ACTIVE, BudgetApprovalAction.FREEZE), BudgetStatus.FROZEN),
            Map.entry(key(BudgetStatus.FROZEN, BudgetApprovalAction.ACTIVATE), BudgetStatus.ACTIVE),
            Map.entry(key(BudgetStatus.ACTIVE, BudgetApprovalAction.CLOSE), BudgetStatus.CLOSED),
            Map.entry(key(BudgetStatus.FROZEN, BudgetApprovalAction.CLOSE), BudgetStatus.CLOSED),
            Map.entry(key(BudgetStatus.CLOSED, BudgetApprovalAction.ARCHIVE), BudgetStatus.ARCHIVED)
    );

    /** @return the resulting status, or {@code null} if the transition is illegal. */
    public static BudgetStatus resolve(BudgetStatus from, BudgetApprovalAction action) {
        return TRANSITIONS.get(key(from, action));
    }

    public static boolean isLegal(BudgetStatus from, BudgetApprovalAction action) {
        return TRANSITIONS.containsKey(key(from, action));
    }

    /** @return resulting status; throws {@link IllegalStateException} if illegal. */
    public static BudgetStatus require(BudgetStatus from, BudgetApprovalAction action) {
        BudgetStatus to = resolve(from, action);
        if (to == null) {
            throw new IllegalStateException(
                    "Illegal budget transition: " + from + " cannot " + action);
        }
        return to;
    }
}
