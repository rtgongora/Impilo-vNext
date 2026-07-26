package zw.gov.mohcc.impilo.pct.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two transitions that make this pack more than a casualty register.
 */
class EmergencyEpisodeStateTest {

    /**
     * The single most important assertion in the pack. If a referral, admission request or theatre
     * request could close an episode, then "we asked someone to take this patient" and "someone
     * took this patient" would be the same record — which is exactly how a patient disappears
     * between services while every individual system looks complete.
     */
    @Test
    @DisplayName("in-care cannot close as handed over without passing through awaiting-acceptance")
    void aRequestCannotCloseAnEpisode() {
        assertFalse(EmergencyEpisodeState.OPEN_IN_CARE
                        .canTransitionTo(EmergencyEpisodeState.CLOSED_HANDED_OVER),
                "raising a request must not close the episode — only an accepted handover does");

        assertTrue(EmergencyEpisodeState.OPEN_IN_CARE
                .canTransitionTo(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE));
        assertTrue(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE
                .canTransitionTo(EmergencyEpisodeState.CLOSED_HANDED_OVER));
    }

    /**
     * The counterpart. A declined or expired handover must give the patient back, because the
     * alternative — leaving them parked in a waiting state nobody owns — is the failure the waiting
     * state was introduced to make visible.
     */
    @Test
    @DisplayName("a declined or expired handover returns the patient to emergency care")
    void aDeclinedHandoverReturnsThePatient() {
        assertTrue(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE
                        .canTransitionTo(EmergencyEpisodeState.OPEN_IN_CARE),
                "there is no timeout that discharges responsibility; the patient comes back");
    }

    @Test
    @DisplayName("a pre-alert must arrive before it can be in care")
    void aPreAlertCannotSkipArrival() {
        assertFalse(EmergencyEpisodeState.PRE_ARRIVAL
                .canTransitionTo(EmergencyEpisodeState.OPEN_IN_CARE));
        assertTrue(EmergencyEpisodeState.PRE_ARRIVAL
                .canTransitionTo(EmergencyEpisodeState.OPEN_UNTRIAGED));
        // A diverted or stood-down ambulance is real and must be recordable.
        assertTrue(EmergencyEpisodeState.PRE_ARRIVAL
                .canTransitionTo(EmergencyEpisodeState.CLOSED_DECLINED_CARE));
    }

    /**
     * Dying or leaving before assessment are real outcomes. A graph that forced every patient
     * through OPEN_IN_CARE first would make the system unable to record what actually happened,
     * and the pressure would be to record something false instead.
     */
    @Test
    @DisplayName("a patient can die or leave before anyone assesses them")
    void untriagedPatientsCanReachRealOutcomes() {
        assertTrue(EmergencyEpisodeState.OPEN_UNTRIAGED
                .canTransitionTo(EmergencyEpisodeState.CLOSED_LEFT_BEFORE_ASSESSMENT));
        assertTrue(EmergencyEpisodeState.OPEN_UNTRIAGED
                .canTransitionTo(EmergencyEpisodeState.CLOSED_DIED));
    }

    @Test
    @DisplayName("every terminal state is genuinely terminal")
    void terminalStatesHaveNoExit() {
        for (EmergencyEpisodeState s : EmergencyEpisodeState.values()) {
            if (s.isTerminal()) {
                assertTrue(s.allowedNext().isEmpty(), s + " is terminal but has outgoing transitions");
                assertFalse(s.isOpen());
            } else {
                assertFalse(s.allowedNext().isEmpty(), s + " is open but has nowhere to go");
                assertTrue(s.isOpen());
            }
        }
    }

    @Test
    @DisplayName("every state is reachable from PRE_ARRIVAL")
    void everyStateIsReachable() {
        var reached = new java.util.HashSet<EmergencyEpisodeState>();
        var queue = new java.util.ArrayDeque<EmergencyEpisodeState>();
        queue.add(EmergencyEpisodeState.PRE_ARRIVAL);
        reached.add(EmergencyEpisodeState.PRE_ARRIVAL);
        while (!queue.isEmpty()) {
            for (EmergencyEpisodeState next : queue.poll().allowedNext()) {
                if (reached.add(next)) queue.add(next);
            }
        }
        Arrays.stream(EmergencyEpisodeState.values()).forEach(s ->
                assertTrue(reached.contains(s),
                        s + " is unreachable — a state nothing can enter is a state nobody maintains"));
    }
}
