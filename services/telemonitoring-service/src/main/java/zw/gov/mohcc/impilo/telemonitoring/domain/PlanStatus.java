package zw.gov.mohcc.impilo.telemonitoring.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * MonitoringPlan lifecycle (Vol II §14.3 field 7): guarded transition machine.
 *
 * <pre>
 * DRAFT -> PENDING_APPROVAL -> ACTIVE -> SUSPENDED -> ACTIVE
 *                                   \-> COMPLETED
 * DRAFT/PENDING_APPROVAL/ACTIVE/SUSPENDED -> CANCELLED
 * COMPLETED / CANCELLED are terminal.
 * </pre>
 *
 * <p>§9 discipline applies: every transition is reason-bound where it removes or pauses
 * care (suspend/complete/cancel), and activation additionally requires the clinician
 * approval gate enforced in {@code MonitoringPlanService} — the machine alone never
 * activates a plan.</p>
 */
public enum PlanStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    CANCELLED;

    private static final Map<PlanStatus, Set<PlanStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(PENDING_APPROVAL, CANCELLED),
            PENDING_APPROVAL, EnumSet.of(ACTIVE, CANCELLED),
            ACTIVE, EnumSet.of(SUSPENDED, COMPLETED, CANCELLED),
            SUSPENDED, EnumSet.of(ACTIVE, COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(PlanStatus.class),
            CANCELLED, EnumSet.noneOf(PlanStatus.class));

    /** True when the machine permits {@code from -> to}. */
    public static boolean canTransition(PlanStatus from, PlanStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    /** True when no further transitions are possible. */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
