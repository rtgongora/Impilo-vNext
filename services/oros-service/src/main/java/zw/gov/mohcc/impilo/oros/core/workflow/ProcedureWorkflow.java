package zw.gov.mohcc.impilo.oros.core.workflow;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.ProcedureWorkflowState;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static zw.gov.mohcc.impilo.oros.domain.ProcedureWorkflowState.*;

/**
 * Deny-by-default transition guard for the procedure / clinical-assessment workflow
 * ({@link ProcedureWorkflowState}). Encodes the scheduling/performance journey (schedule →
 * arrive → perform → report) plus the shared report lifecycle and exception set.
 */
@Component
public class ProcedureWorkflow extends EnumFulfilmentWorkflow<ProcedureWorkflowState> {

    /** Exceptions reachable while the order is still operationally open (pre-report). */
    private static final Set<ProcedureWorkflowState> OPEN_EXCEPTIONS =
            EnumSet.of(CANCELLED, REJECTED, RETURNED_FOR_CLARIFICATION, DEFERRED, REASSIGNED);

    private static final Map<ProcedureWorkflowState, Set<ProcedureWorkflowState>> TRANSITIONS = Map.ofEntries(
            Map.entry(RECEIVED, EnumSet.of(ACCEPTED, REJECTED, RETURNED_FOR_CLARIFICATION, CANCELLED, REASSIGNED)),
            Map.entry(ACCEPTED, plus(EnumSet.of(SCHEDULED, ARRIVED, IN_PROGRESS), OPEN_EXCEPTIONS)),
            Map.entry(SCHEDULED, plus(EnumSet.of(ARRIVED, IN_PROGRESS, NO_SHOW), OPEN_EXCEPTIONS)),
            Map.entry(ARRIVED, plus(EnumSet.of(IN_PROGRESS), OPEN_EXCEPTIONS)),
            Map.entry(IN_PROGRESS, EnumSet.of(PERFORMED, DEFERRED, CANCELLED)),
            Map.entry(PERFORMED, EnumSet.of(REPORT_PENDING, PRELIMINARY_REPORT, FINAL_REPORT)),
            Map.entry(REPORT_PENDING, EnumSet.of(PRELIMINARY_REPORT, FINAL_REPORT)),
            Map.entry(PRELIMINARY_REPORT, EnumSet.of(FINAL_REPORT, AMENDED)),
            Map.entry(FINAL_REPORT, EnumSet.of(RELEASED, AMENDED)),
            Map.entry(RELEASED, EnumSet.of(ACKNOWLEDGED, AMENDED, SUPERSEDED)),
            Map.entry(ACKNOWLEDGED, EnumSet.of(CLOSED, AMENDED, SUPERSEDED)),
            Map.entry(AMENDED, EnumSet.of(RELEASED, FINAL_REPORT, SUPERSEDED)),
            Map.entry(RETURNED_FOR_CLARIFICATION, EnumSet.of(RECEIVED, ACCEPTED, CANCELLED)),
            Map.entry(REASSIGNED, EnumSet.of(RECEIVED, ACCEPTED, CANCELLED)),
            Map.entry(DEFERRED, EnumSet.of(SCHEDULED, ARRIVED, IN_PROGRESS, CANCELLED)),
            Map.entry(NO_SHOW, EnumSet.of(SCHEDULED, CANCELLED)),
            // Terminal
            Map.entry(CLOSED, EnumSet.noneOf(ProcedureWorkflowState.class)),
            Map.entry(REJECTED, EnumSet.noneOf(ProcedureWorkflowState.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(ProcedureWorkflowState.class)),
            Map.entry(SUPERSEDED, EnumSet.noneOf(ProcedureWorkflowState.class))
    );

    public ProcedureWorkflow() {
        super(ProcedureWorkflowState.class);
    }

    @Override
    public OrderType orderType() {
        return OrderType.PROCEDURE;
    }

    @Override
    protected Map<ProcedureWorkflowState, Set<ProcedureWorkflowState>> transitions() {
        return TRANSITIONS;
    }

    @Override
    protected ProcedureWorkflowState entry() {
        return RECEIVED;
    }

    private static Set<ProcedureWorkflowState> plus(Set<ProcedureWorkflowState> base,
                                                    Set<ProcedureWorkflowState> extra) {
        EnumSet<ProcedureWorkflowState> s = EnumSet.copyOf(base);
        s.addAll(extra);
        return s;
    }
}
