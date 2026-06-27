package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState.*;

/**
 * Unit tests for the {@link ImagingWorkflow} transition guard (spec §3 lifecycle).
 */
class ImagingWorkflowTest {

    @Test
    void entryPointIsReceived() {
        assertThat(ImagingWorkflow.canTransition(null, RECEIVED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(null, IN_PROGRESS)).isFalse();
    }

    @Test
    void happyPathProgressesThroughTheLifecycle() {
        assertThat(ImagingWorkflow.canTransition(RECEIVED, ACCEPTED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(ACCEPTED, SCHEDULED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(SCHEDULED, ARRIVED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(ARRIVED, IN_PROGRESS)).isTrue();
        assertThat(ImagingWorkflow.canTransition(IN_PROGRESS, PERFORMED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(PERFORMED, AWAITING_IMAGES)).isTrue();
        assertThat(ImagingWorkflow.canTransition(AWAITING_IMAGES, IMAGES_LINKED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(IMAGES_LINKED, AWAITING_REPORT)).isTrue();
        assertThat(ImagingWorkflow.canTransition(AWAITING_REPORT, FINAL_REPORT)).isTrue();
        assertThat(ImagingWorkflow.canTransition(FINAL_REPORT, RELEASED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(RELEASED, ACKNOWLEDGED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(ACKNOWLEDGED, CLOSED)).isTrue();
    }

    @Test
    void illegalSkipsAreRejected() {
        assertThat(ImagingWorkflow.canTransition(RECEIVED, RELEASED)).isFalse();
        assertThat(ImagingWorkflow.canTransition(IN_PROGRESS, ACKNOWLEDGED)).isFalse();
        assertThat(ImagingWorkflow.canTransition(SCHEDULED, FINAL_REPORT)).isFalse();
    }

    @Test
    void exceptionsArePermittedFromOperationalStates() {
        assertThat(ImagingWorkflow.canTransition(RECEIVED, REJECTED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(SCHEDULED, NO_SHOW)).isTrue();
        assertThat(ImagingWorkflow.canTransition(ACCEPTED, RETURNED_FOR_CLARIFICATION)).isTrue();
        assertThat(ImagingWorkflow.canTransition(IN_PROGRESS, FAILED)).isTrue();
    }

    @Test
    void amendmentReopensReportCycleAndCanRerelease() {
        assertThat(ImagingWorkflow.canTransition(RELEASED, AMENDED)).isTrue();
        assertThat(ImagingWorkflow.canTransition(AMENDED, RELEASED)).isTrue();
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        assertThat(ImagingWorkflow.allowedFrom(CANCELLED)).isEmpty();
        assertThat(ImagingWorkflow.allowedFrom(REJECTED)).isEmpty();
        assertThat(ImagingWorkflow.allowedFrom(CLOSED)).isEmpty();
        assertThat(ImagingWorkflow.allowedFrom(SUPERSEDED)).isEmpty();
    }

    @Test
    void requireThrowsOnIllegalTransition() {
        assertThatThrownBy(() -> ImagingWorkflow.require(RECEIVED, RELEASED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid imaging workflow transition");
        assertThat(ImagingWorkflow.require(RECEIVED, ACCEPTED)).isEqualTo(ACCEPTED);
    }
}
