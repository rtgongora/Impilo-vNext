package zw.gov.mohcc.impilo.tuso.core;

import java.util.Map;
import java.util.Set;

/**
 * Pure CSSD (Central Sterile Services Department) decontamination state machine for an instrument set.
 * TUSO owns the instrument_set registry + its sterilisation lifecycle; this class encodes the legal
 * transitions so the write service (and its unit tests) share one source of truth.
 *
 * <pre>
 *   STERILE --ISSUE--&gt; IN_USE --RETURN--&gt; SOILED --REPROCESS--&gt; REPROCESSING
 *                                                      --STERILISE--&gt; STERILISED --RELEASE--&gt; STERILE
 *   STERILE --EXPIRE--&gt; EXPIRED --REPROCESS--&gt; REPROCESSING (an expired set is re-autoclaved)
 * </pre>
 *
 * STERILE is the ONLY state a set may be issued to a theatre case from. A contaminated / incomplete
 * return does not change the transitions — it is recorded as a flag on the cycle and still routes the
 * set through reprocessing (that is precisely what CSSD is for).
 */
public final class CssdCycleStateMachine {

    private CssdCycleStateMachine() {}

    public enum State { STERILE, IN_USE, SOILED, REPROCESSING, STERILISED, EXPIRED }

    public enum Action { ISSUE, RETURN, REPROCESS, STERILISE, RELEASE, EXPIRE }

    /** Legal (fromState → action) → toState transitions. Anything absent is illegal. */
    private static final Map<State, Map<Action, State>> TRANSITIONS = Map.of(
            State.STERILE, Map.of(Action.ISSUE, State.IN_USE, Action.EXPIRE, State.EXPIRED),
            State.IN_USE, Map.of(Action.RETURN, State.SOILED),
            State.SOILED, Map.of(Action.REPROCESS, State.REPROCESSING),
            State.EXPIRED, Map.of(Action.REPROCESS, State.REPROCESSING),
            State.REPROCESSING, Map.of(Action.STERILISE, State.STERILISED),
            State.STERILISED, Map.of(Action.RELEASE, State.STERILE));

    /** Non-terminal cycle-log states (the fine-grained decontamination FSM, distinct from the set state). */
    public static final Set<String> CYCLE_STATES =
            Set.of("USE", "SOILED", "REPROCESSING", "STERILISED", "STERILE");

    public static State parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("null sterilisation state");
        return State.valueOf(raw.trim().toUpperCase());
    }

    public static boolean canTransition(State from, Action action) {
        Map<Action, State> byAction = TRANSITIONS.get(from);
        return byAction != null && byAction.containsKey(action);
    }

    /** Apply an action to a state, or throw {@link IllegalStateException} if the transition is illegal. */
    public static State transition(State from, Action action) {
        Map<Action, State> byAction = TRANSITIONS.get(from);
        if (byAction == null || !byAction.containsKey(action)) {
            throw new IllegalStateException(
                    "Illegal CSSD transition: " + action + " not allowed from " + from);
        }
        return byAction.get(action);
    }
}
