package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure state-machine coverage for the EMS mission lifecycle transitions. */
class EmsMissionStateTest {

    @Test
    void happyPathWalksCreatedToCleared() {
        EmsMissionState[] path = {
                EmsMissionState.CREATED, EmsMissionState.DISPATCHED, EmsMissionState.ACKNOWLEDGED,
                EmsMissionState.ACCEPTED, EmsMissionState.EN_ROUTE_SCENE, EmsMissionState.ON_SCENE,
                EmsMissionState.PATIENT_CONTACT, EmsMissionState.DEPARTED_SCENE,
                EmsMissionState.EN_ROUTE_FACILITY, EmsMissionState.ARRIVED_FACILITY,
                EmsMissionState.HANDOVER, EmsMissionState.CLEARED
        };
        for (int i = 0; i < path.length - 1; i++) {
            assertThat(path[i].canTransitionTo(path[i + 1]))
                    .as("%s -> %s", path[i], path[i + 1]).isTrue();
        }
    }

    @Test
    void skippingStatesIsIllegal() {
        assertThat(EmsMissionState.CREATED.canTransitionTo(EmsMissionState.ON_SCENE)).isFalse();
        assertThat(EmsMissionState.DISPATCHED.canTransitionTo(EmsMissionState.HANDOVER)).isFalse();
        assertThat(EmsMissionState.ARRIVED_FACILITY.canTransitionTo(EmsMissionState.CLEARED)).isFalse();
    }

    @Test
    void standdownAndCancelAreEarlyExitsBeforePatientContact() {
        assertThat(EmsMissionState.EN_ROUTE_SCENE.canTransitionTo(EmsMissionState.STANDDOWN)).isTrue();
        assertThat(EmsMissionState.ON_SCENE.canTransitionTo(EmsMissionState.CANCELLED)).isTrue();
        // After patient contact the crew is committed to the patient — no stand-down.
        assertThat(EmsMissionState.PATIENT_CONTACT.canTransitionTo(EmsMissionState.STANDDOWN)).isFalse();
        assertThat(EmsMissionState.PATIENT_CONTACT.canTransitionTo(EmsMissionState.CANCELLED)).isTrue();
    }

    @Test
    void terminalStatesAllowNoFurtherTransition() {
        assertThat(EmsMissionState.CLEARED.isTerminal()).isTrue();
        assertThat(EmsMissionState.STANDDOWN.isTerminal()).isTrue();
        assertThat(EmsMissionState.CANCELLED.isTerminal()).isTrue();
        assertThat(EmsMissionState.HANDOVER.isTerminal()).isFalse();
    }

    @Test
    void ofRejectsUnknownState() {
        assertThatThrownBy(() -> EmsMissionState.of("TELEPORTED"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(EmsMissionState.of("handover")).isEqualTo(EmsMissionState.HANDOVER);
    }
}
