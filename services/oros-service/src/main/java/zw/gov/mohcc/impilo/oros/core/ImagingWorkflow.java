package zw.gov.mohcc.impilo.oros.core;

import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState.*;

/**
 * Transition guard for the diagnostic/imaging workflow ({@link ImagingWorkflowState}).
 *
 * <p>Encodes the spec §3 lifecycle as an explicit, deny-by-default transition map. Exception
 * transitions (reject/clarify/cancel/defer/no-show/fail/reassign/amend/supersede) are permitted
 * from the relevant operational states. Terminal states allow no further transitions.</p>
 */
public final class ImagingWorkflow {

    private ImagingWorkflow() {
    }

    /** Exceptions reachable while the order is still operationally open (pre-report). */
    private static final Set<ImagingWorkflowState> OPEN_EXCEPTIONS =
            EnumSet.of(CANCELLED, REJECTED, RETURNED_FOR_CLARIFICATION, DEFERRED, REASSIGNED);

    private static final Map<ImagingWorkflowState, Set<ImagingWorkflowState>> TRANSITIONS = Map.ofEntries(
            Map.entry(RECEIVED, EnumSet.of(ACCEPTED, REJECTED, RETURNED_FOR_CLARIFICATION, CANCELLED, REASSIGNED)),
            Map.entry(ACCEPTED, plus(EnumSet.of(SCHEDULED, ARRIVED, IN_PROGRESS), OPEN_EXCEPTIONS)),
            Map.entry(SCHEDULED, plus(EnumSet.of(ARRIVED, IN_PROGRESS, NO_SHOW), OPEN_EXCEPTIONS)),
            Map.entry(ARRIVED, plus(EnumSet.of(IN_PROGRESS), OPEN_EXCEPTIONS)),
            Map.entry(IN_PROGRESS, EnumSet.of(PERFORMED, FAILED, DEFERRED, CANCELLED)),
            Map.entry(PERFORMED, EnumSet.of(AWAITING_IMAGES, IMAGES_LINKED, AWAITING_REPORT, FAILED)),
            Map.entry(AWAITING_IMAGES, EnumSet.of(IMAGES_LINKED, FAILED)),
            Map.entry(IMAGES_LINKED, EnumSet.of(AWAITING_REPORT, PRELIMINARY_REPORT, FINAL_REPORT)),
            Map.entry(AWAITING_REPORT, EnumSet.of(PRELIMINARY_REPORT, FINAL_REPORT)),
            Map.entry(PRELIMINARY_REPORT, EnumSet.of(FINAL_REPORT, AMENDED)),
            Map.entry(FINAL_REPORT, EnumSet.of(RELEASED, AMENDED)),
            Map.entry(RELEASED, EnumSet.of(ACKNOWLEDGED, AMENDED, SUPERSEDED)),
            Map.entry(ACKNOWLEDGED, EnumSet.of(CLOSED, AMENDED, SUPERSEDED)),
            // An amendment produces a new report cycle; a superseded/amended report can be re-released.
            Map.entry(AMENDED, EnumSet.of(RELEASED, FINAL_REPORT, SUPERSEDED)),
            // Reassignment / clarification return the order to the intake/operational phase.
            Map.entry(RETURNED_FOR_CLARIFICATION, EnumSet.of(RECEIVED, ACCEPTED, CANCELLED)),
            Map.entry(REASSIGNED, EnumSet.of(RECEIVED, ACCEPTED, CANCELLED)),
            Map.entry(DEFERRED, EnumSet.of(SCHEDULED, ARRIVED, IN_PROGRESS, CANCELLED)),
            Map.entry(NO_SHOW, EnumSet.of(SCHEDULED, CANCELLED)),
            Map.entry(FAILED, EnumSet.of(IN_PROGRESS, CANCELLED)),
            // Terminal
            Map.entry(CLOSED, EnumSet.noneOf(ImagingWorkflowState.class)),
            Map.entry(REJECTED, EnumSet.noneOf(ImagingWorkflowState.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(ImagingWorkflowState.class)),
            Map.entry(SUPERSEDED, EnumSet.noneOf(ImagingWorkflowState.class))
    );

    public static boolean canTransition(ImagingWorkflowState from, ImagingWorkflowState to) {
        if (from == null) {
            return to == RECEIVED; // entry point
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<ImagingWorkflowState> allowedFrom(ImagingWorkflowState from) {
        if (from == null) {
            return EnumSet.of(RECEIVED);
        }
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    /** Validates and returns {@code to}, throwing on an illegal transition. */
    public static ImagingWorkflowState require(ImagingWorkflowState from, ImagingWorkflowState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Invalid imaging workflow transition from " + from + " to " + to);
        }
        return to;
    }

    private static Set<ImagingWorkflowState> plus(Set<ImagingWorkflowState> base,
                                                  Set<ImagingWorkflowState> extra) {
        EnumSet<ImagingWorkflowState> s = EnumSet.copyOf(base);
        s.addAll(extra);
        return s;
    }
}
