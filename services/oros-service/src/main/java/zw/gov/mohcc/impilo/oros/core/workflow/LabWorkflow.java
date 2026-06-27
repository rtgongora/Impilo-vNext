package zw.gov.mohcc.impilo.oros.core.workflow;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.domain.LabWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static zw.gov.mohcc.impilo.oros.domain.LabWorkflowState.*;

/**
 * Deny-by-default transition guard for the laboratory-investigation workflow
 * ({@link LabWorkflowState}). Encodes the specimen-centric journey (collection → dispatch →
 * receipt → analysis → validation → release → acknowledge → close) plus the lab exception set.
 */
@Component
public class LabWorkflow extends EnumFulfilmentWorkflow<LabWorkflowState> {

    /** Exceptions reachable while the order is still operationally open (pre-result). */
    private static final Set<LabWorkflowState> OPEN_EXCEPTIONS =
            EnumSet.of(CANCELLED, REJECTED, RETURNED_FOR_CLARIFICATION, DEFERRED, REASSIGNED);

    private static final Map<LabWorkflowState, Set<LabWorkflowState>> TRANSITIONS = Map.ofEntries(
            Map.entry(RECEIVED, plus(EnumSet.of(ACCEPTED, AWAITING_COLLECTION, COLLECTED), OPEN_EXCEPTIONS)),
            Map.entry(ACCEPTED, plus(EnumSet.of(AWAITING_COLLECTION, COLLECTED), OPEN_EXCEPTIONS)),
            Map.entry(AWAITING_COLLECTION, plus(EnumSet.of(COLLECTED), OPEN_EXCEPTIONS)),
            Map.entry(COLLECTED, EnumSet.of(DISPATCHED, RECEIVED_IN_LAB, SPECIMEN_REJECTED,
                    SPECIMEN_LOST, RECOLLECT_REQUESTED, CANCELLED)),
            Map.entry(DISPATCHED, EnumSet.of(IN_TRANSIT, RECEIVED_IN_LAB, SPECIMEN_LOST)),
            Map.entry(IN_TRANSIT, EnumSet.of(RECEIVED_IN_LAB, SPECIMEN_LOST)),
            Map.entry(RECEIVED_IN_LAB, EnumSet.of(IN_PROGRESS, SPECIMEN_REJECTED, RECOLLECT_REQUESTED)),
            Map.entry(IN_PROGRESS, EnumSet.of(PRELIMINARY_RESULT, RESULT_VALIDATED, FINAL_RESULT,
                    COULD_NOT_PERFORM)),
            Map.entry(PRELIMINARY_RESULT, EnumSet.of(RESULT_VALIDATED, FINAL_RESULT, AMENDED)),
            Map.entry(RESULT_VALIDATED, EnumSet.of(FINAL_RESULT, RELEASED)),
            Map.entry(FINAL_RESULT, EnumSet.of(RELEASED, AMENDED, CORRECTED)),
            Map.entry(RELEASED, EnumSet.of(ACKNOWLEDGED, AMENDED, CORRECTED, SUPERSEDED)),
            Map.entry(ACKNOWLEDGED, EnumSet.of(CLOSED, AMENDED, CORRECTED, SUPERSEDED)),
            // Amendments/corrections produce a new report cycle.
            Map.entry(AMENDED, EnumSet.of(RELEASED, FINAL_RESULT, SUPERSEDED)),
            Map.entry(CORRECTED, EnumSet.of(RELEASED, FINAL_RESULT, SUPERSEDED)),
            // Specimen exceptions recover via recollection.
            Map.entry(SPECIMEN_REJECTED, EnumSet.of(RECOLLECT_REQUESTED, CANCELLED)),
            Map.entry(SPECIMEN_LOST, EnumSet.of(RECOLLECT_REQUESTED, CANCELLED)),
            Map.entry(RECOLLECT_REQUESTED, EnumSet.of(AWAITING_COLLECTION, COLLECTED, CANCELLED)),
            Map.entry(COULD_NOT_PERFORM, EnumSet.of(RECOLLECT_REQUESTED, CANCELLED, CLOSED)),
            Map.entry(DEFERRED, EnumSet.of(AWAITING_COLLECTION, COLLECTED, CANCELLED)),
            Map.entry(RETURNED_FOR_CLARIFICATION, EnumSet.of(RECEIVED, ACCEPTED, CANCELLED)),
            Map.entry(REASSIGNED, EnumSet.of(RECEIVED, ACCEPTED, CANCELLED)),
            // Terminal
            Map.entry(CLOSED, EnumSet.noneOf(LabWorkflowState.class)),
            Map.entry(REJECTED, EnumSet.noneOf(LabWorkflowState.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(LabWorkflowState.class)),
            Map.entry(SUPERSEDED, EnumSet.noneOf(LabWorkflowState.class))
    );

    public LabWorkflow() {
        super(LabWorkflowState.class);
    }

    @Override
    public OrderType orderType() {
        return OrderType.LAB;
    }

    @Override
    protected Map<LabWorkflowState, Set<LabWorkflowState>> transitions() {
        return TRANSITIONS;
    }

    @Override
    protected LabWorkflowState entry() {
        return RECEIVED;
    }

    private static Set<LabWorkflowState> plus(Set<LabWorkflowState> base, Set<LabWorkflowState> extra) {
        EnumSet<LabWorkflowState> s = EnumSet.copyOf(base);
        s.addAll(extra);
        return s;
    }
}
