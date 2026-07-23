package zw.gov.mohcc.impilo.telemonitoring.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus.ACTIVE;
import static zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus.ASSIGNED;
import static zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus.LOST;
import static zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus.RETURNED;
import static zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus.SUSPENDED;
import static zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus.canTransition;

/** OF-B24 §15.5/§15.6 — the device-assignment guarded transition machine. */
class DeviceAssignmentStatusMachineTest {

    @Test
    void happyPathAssignActivateSuspendResumeReturn() {
        assertThat(canTransition(ASSIGNED, ACTIVE)).isTrue();
        assertThat(canTransition(ACTIVE, SUSPENDED)).isTrue();
        assertThat(canTransition(SUSPENDED, ACTIVE)).isTrue();
        assertThat(canTransition(ACTIVE, RETURNED)).isTrue();
    }

    @Test
    void everyNonTerminalStateCanCloseAsReturnedOrLost() {
        for (DeviceAssignmentStatus from : new DeviceAssignmentStatus[]{ASSIGNED, ACTIVE, SUSPENDED}) {
            assertThat(canTransition(from, RETURNED)).as("%s -> RETURNED", from).isTrue();
            assertThat(canTransition(from, LOST)).as("%s -> LOST", from).isTrue();
        }
    }

    @Test
    void terminalStatesPermitNothing() {
        for (DeviceAssignmentStatus terminal : new DeviceAssignmentStatus[]{RETURNED, LOST}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (DeviceAssignmentStatus to : DeviceAssignmentStatus.values()) {
                assertThat(canTransition(terminal, to)).as("%s -> %s", terminal, to).isFalse();
            }
        }
    }

    @Test
    void illegalJumpsAreRefused() {
        // Activation cannot be skipped backwards, suspension needs an active binding.
        assertThat(canTransition(ASSIGNED, SUSPENDED)).isFalse();
        assertThat(canTransition(ACTIVE, ASSIGNED)).isFalse();
        assertThat(canTransition(SUSPENDED, ASSIGNED)).isFalse();
        assertThat(canTransition(RETURNED, ACTIVE)).isFalse();
        assertThat(canTransition(LOST, RETURNED)).isFalse();
        assertThat(canTransition(null, ACTIVE)).isFalse();
        assertThat(canTransition(ACTIVE, null)).isFalse();
    }

    @Test
    void nonTerminalStatesAreExactlyTheGuardedSet() {
        assertThat(ASSIGNED.isTerminal()).isFalse();
        assertThat(ACTIVE.isTerminal()).isFalse();
        assertThat(SUSPENDED.isTerminal()).isFalse();
    }
}
