package zw.gov.mohcc.impilo.daidzai.core;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * EMS clinical-dispatch mission state machine (architecture decision #2). A crew is dispatched to a
 * patient and progresses through prehospital contact to a facility handover. The happy path is:
 *
 * <pre>CREATED → DISPATCHED → ACKNOWLEDGED → ACCEPTED → EN_ROUTE_SCENE → ON_SCENE →
 *      PATIENT_CONTACT → DEPARTED_SCENE → EN_ROUTE_FACILITY → ARRIVED_FACILITY → HANDOVER → CLEARED</pre>
 *
 * with {@link #STANDDOWN} / {@link #CANCELLED} as early exits before patient contact, and
 * {@link #CLEARED} / {@link #STANDDOWN} / {@link #CANCELLED} terminal. Transitions are validated;
 * an illegal transition is rejected (never silently applied).
 */
public enum EmsMissionState {
    CREATED,
    DISPATCHED,
    ACKNOWLEDGED,
    ACCEPTED,
    EN_ROUTE_SCENE,
    ON_SCENE,
    PATIENT_CONTACT,
    DEPARTED_SCENE,
    EN_ROUTE_FACILITY,
    ARRIVED_FACILITY,
    HANDOVER,
    CLEARED,
    STANDDOWN,
    CANCELLED;

    /** Early-exit states reachable from any pre-patient-contact state. */
    private static final Set<EmsMissionState> EARLY_EXIT = EnumSet.of(STANDDOWN, CANCELLED);

    private static final Map<EmsMissionState, Set<EmsMissionState>> NEXT = Map.ofEntries(
            Map.entry(CREATED, EnumSet.of(DISPATCHED, STANDDOWN, CANCELLED)),
            Map.entry(DISPATCHED, EnumSet.of(ACKNOWLEDGED, STANDDOWN, CANCELLED)),
            Map.entry(ACKNOWLEDGED, EnumSet.of(ACCEPTED, STANDDOWN, CANCELLED)),
            Map.entry(ACCEPTED, EnumSet.of(EN_ROUTE_SCENE, STANDDOWN, CANCELLED)),
            Map.entry(EN_ROUTE_SCENE, EnumSet.of(ON_SCENE, STANDDOWN, CANCELLED)),
            Map.entry(ON_SCENE, EnumSet.of(PATIENT_CONTACT, STANDDOWN, CANCELLED)),
            // After patient contact the crew is committed to the patient: no stand-down, only cancel.
            Map.entry(PATIENT_CONTACT, EnumSet.of(DEPARTED_SCENE, CANCELLED)),
            Map.entry(DEPARTED_SCENE, EnumSet.of(EN_ROUTE_FACILITY, CANCELLED)),
            Map.entry(EN_ROUTE_FACILITY, EnumSet.of(ARRIVED_FACILITY, CANCELLED)),
            Map.entry(ARRIVED_FACILITY, EnumSet.of(HANDOVER)),
            Map.entry(HANDOVER, EnumSet.of(CLEARED)),
            Map.entry(CLEARED, EnumSet.noneOf(EmsMissionState.class)),
            Map.entry(STANDDOWN, EnumSet.noneOf(EmsMissionState.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(EmsMissionState.class))
    );

    /** True if {@code this → target} is a legal transition. */
    public boolean canTransitionTo(EmsMissionState target) {
        return NEXT.getOrDefault(this, EnumSet.noneOf(EmsMissionState.class)).contains(target);
    }

    public boolean isTerminal() {
        return NEXT.getOrDefault(this, EnumSet.noneOf(EmsMissionState.class)).isEmpty();
    }

    public boolean isEarlyExit() {
        return EARLY_EXIT.contains(this);
    }

    public static EmsMissionState of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("mission state is required");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown EMS mission state: " + raw);
        }
    }
}
