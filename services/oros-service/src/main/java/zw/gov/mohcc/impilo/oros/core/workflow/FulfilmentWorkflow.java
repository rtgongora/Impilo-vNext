package zw.gov.mohcc.impilo.oros.core.workflow;

import zw.gov.mohcc.impilo.oros.domain.OrderType;

import java.util.Set;

/**
 * Deny-by-default transition guard for a single order category's fine-grained fulfilment
 * lifecycle. Implementations speak in <em>state names</em> (the value persisted to
 * {@code oros_orders.workflow_state}) so the shared column stays category-agnostic while each
 * category enforces its own legal-transition graph.
 *
 * <p>One implementation exists per {@link OrderType} that has a granular journey (imaging, lab,
 * procedure); the {@link WorkflowGuardRegistry} selects the right one for an order. Categories
 * without a fine-grained journey (e.g. PHARMACY, which is workstep-driven) simply have no guard.</p>
 */
public interface FulfilmentWorkflow {

    /** The order category this guard governs. */
    OrderType orderType();

    /** The entry state set when a fresh order of this category enters fulfilment. */
    String entryState();

    /** All valid state names for this category. */
    Set<String> states();

    /**
     * Whether {@code to} is a legal next state from {@code from}. A {@code null} {@code from}
     * means "not yet in fulfilment" and only permits the {@link #entryState()}. Unknown state
     * names yield {@code false} (deny-by-default).
     */
    boolean canTransition(String from, String to);

    /** Legal next states from {@code from} ({@link #entryState()} when {@code from} is null). */
    Set<String> allowedFrom(String from);

    /** Validates and returns {@code to}, throwing on an illegal transition. */
    default String require(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid " + orderType() + " workflow transition from "
                    + from + " to " + to);
        }
        return to;
    }
}
