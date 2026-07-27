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
            // A proposal is not an order. It may become one, be declined, or lapse — and it is
            // visible while it waits, which is the point: a recommendation nobody actioned is
            // exactly the kind of request that disappears today.
            Map.entry(PROPOSED, EnumSet.of(RECEIVED, REJECTED, CANCELLED)),
            Map.entry(RECEIVED, EnumSet.of(ACCEPTED, REJECTED, RETURNED_FOR_CLARIFICATION, CANCELLED, REASSIGNED)),
            Map.entry(ACCEPTED, plus(EnumSet.of(SCHEDULED, ARRIVED, IN_PROGRESS), OPEN_EXCEPTIONS)),
            Map.entry(SCHEDULED, plus(EnumSet.of(ARRIVED, IN_PROGRESS, NO_SHOW), OPEN_EXCEPTIONS)),
            Map.entry(ARRIVED, plus(EnumSet.of(IN_PROGRESS), OPEN_EXCEPTIONS)),
            // A procedure in progress can end four ways, not one. Collapsing aborted, failed
            // and partial into PERFORMED is how a service reports a completion rate it has not
            // earned, and it is why §26 asks for these separately.
            Map.entry(IN_PROGRESS, EnumSet.of(PERFORMED, ABORTED, FAILED, PARTIALLY_COMPLETED,
                    DEFERRED, CANCELLED)),
            Map.entry(PERFORMED, EnumSet.of(REPORT_PENDING, PRELIMINARY_REPORT, FINAL_REPORT)),
            // An aborted, failed or partial procedure still produces findings worth reporting,
            // and may be repeated. It is never simply dropped.
            Map.entry(ABORTED, EnumSet.of(REPORT_PENDING, PRELIMINARY_REPORT, FINAL_REPORT, REPEATED, CANCELLED)),
            Map.entry(FAILED, EnumSet.of(REPORT_PENDING, PRELIMINARY_REPORT, FINAL_REPORT, REPEATED, CANCELLED)),
            Map.entry(PARTIALLY_COMPLETED, EnumSet.of(REPORT_PENDING, PRELIMINARY_REPORT, FINAL_REPORT, REPEATED)),
            Map.entry(REPEATED, EnumSet.of(SCHEDULED, ARRIVED, IN_PROGRESS, CANCELLED)),
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

    /**
     * The procedure category adopts §4's reason-and-next-action rule. Mirrors the CHECK
     * constraint added in V300, so the rule holds whether a write arrives through this service
     * or around it — a service-only guard is advisory to anything with a database connection.
     */
    @Override
    public Set<String> nonProgressingStates() {
        return Set.of(REJECTED.name(), CANCELLED.name(), DEFERRED.name(), ABORTED.name(),
                FAILED.name(), NO_SHOW.name(), RETURNED_FOR_CLARIFICATION.name());
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
