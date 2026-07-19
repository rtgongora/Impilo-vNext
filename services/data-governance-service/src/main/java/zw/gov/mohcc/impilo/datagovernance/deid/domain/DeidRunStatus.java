package zw.gov.mohcc.impilo.datagovernance.deid.domain;

import java.util.Map;
import java.util.Set;

/**
 * De-identification run FSM: {@code REQUESTED → TRANSFORMING → QA → RELEASED | REJECTED}.
 *
 * <p>{@code REJECTED} is reachable from every non-terminal state so that
 * transform failures (missing subject key, unresolvable dataset secret,
 * detected identifier leak) fail closed instead of erroring out with the run
 * stuck mid-state. {@code RELEASED} is only reachable from {@code QA}.</p>
 */
public enum DeidRunStatus {

    REQUESTED,
    TRANSFORMING,
    QA,
    RELEASED,
    REJECTED;

    private static final Map<DeidRunStatus, Set<DeidRunStatus>> ALLOWED = Map.of(
            REQUESTED, Set.of(TRANSFORMING, REJECTED),
            TRANSFORMING, Set.of(QA, REJECTED),
            QA, Set.of(RELEASED, REJECTED),
            RELEASED, Set.of(),
            REJECTED, Set.of());

    public boolean canTransitionTo(DeidRunStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
