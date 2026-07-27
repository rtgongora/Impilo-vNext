package zw.gov.mohcc.impilo.oros.core.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zw.gov.mohcc.impilo.oros.domain.ProcedureWorkflowState.*;

/**
 * The five states the Clinical Procedures Pipeline §4 required and the guard did not have,
 * plus the non-progressing contract.
 *
 * <p>OROS already owned nineteen states, so this wave adds rather than rebuilds. The tests
 * assert both directions: that the new transitions are legal, and that the ones which would
 * let a service overstate its completion rate are not.</p>
 */
class ProcedureRequestLifecycleTest {

    private final ProcedureWorkflow workflow = new ProcedureWorkflow();

    @Test
    void aProposalIsNotAnOrderButCanBecomeOne() {
        assertThat(workflow.canTransition(PROPOSED.name(), RECEIVED.name())).isTrue();
        assertThat(workflow.canTransition(PROPOSED.name(), REJECTED.name())).isTrue();
        // A proposal cannot skip being ordered and jump straight into execution.
        assertThat(workflow.canTransition(PROPOSED.name(), IN_PROGRESS.name())).isFalse();
        assertThat(workflow.canTransition(PROPOSED.name(), PERFORMED.name())).isFalse();
    }

    @Test
    void aProcedureInProgressCanEndFourWaysNotOne() {
        assertThat(workflow.canTransition(IN_PROGRESS.name(), PERFORMED.name())).isTrue();
        assertThat(workflow.canTransition(IN_PROGRESS.name(), ABORTED.name())).isTrue();
        assertThat(workflow.canTransition(IN_PROGRESS.name(), FAILED.name())).isTrue();
        assertThat(workflow.canTransition(IN_PROGRESS.name(), PARTIALLY_COMPLETED.name())).isTrue();
    }

    @Test
    void anAbortedOrFailedProcedureStillReportsAndMayBeRepeated() {
        // Findings from an abandoned attempt still matter, and the request is not simply dropped.
        assertThat(workflow.canTransition(ABORTED.name(), FINAL_REPORT.name())).isTrue();
        assertThat(workflow.canTransition(FAILED.name(), REPEATED.name())).isTrue();
        assertThat(workflow.canTransition(PARTIALLY_COMPLETED.name(), REPEATED.name())).isTrue();
        assertThat(workflow.canTransition(REPEATED.name(), IN_PROGRESS.name())).isTrue();
    }

    @Test
    void anAbortedProcedureCannotBeRelabelledAsPerformed() {
        // The transition that would let a service report a completion rate it has not earned.
        assertThat(workflow.canTransition(ABORTED.name(), PERFORMED.name())).isFalse();
        assertThat(workflow.canTransition(FAILED.name(), PERFORMED.name())).isFalse();
        assertThat(workflow.canTransition(PARTIALLY_COMPLETED.name(), PERFORMED.name())).isFalse();
    }

    @Test
    void theProcedureCategoryAdoptsTheReasonAndNextActionRule() {
        assertThat(workflow.nonProgressingStates())
                .containsExactlyInAnyOrder(REJECTED.name(), CANCELLED.name(), DEFERRED.name(),
                        ABORTED.name(), FAILED.name(), NO_SHOW.name(),
                        RETURNED_FOR_CLARIFICATION.name());
        // PERFORMED is an outcome, not a stall — it must not demand a "next action".
        assertThat(workflow.nonProgressingStates()).doesNotContain(PERFORMED.name(), CLOSED.name());
    }

    @Test
    void otherCategoriesAreUnaffectedByTheRule() {
        // Opt-in per category. Imaging and lab cancel without a reason today, and changing that
        // is their owners' decision, not a side effect of this wave.
        assertThat(new LabWorkflow().nonProgressingStates()).isEmpty();
        assertThat(new ImagingFulfilmentWorkflow().nonProgressingStates()).isEmpty();
    }

    @Test
    void deniesByDefaultForUnknownStates() {
        assertThat(workflow.canTransition(IN_PROGRESS.name(), "NOT_A_STATE")).isFalse();
        assertThat(workflow.canTransition("NOT_A_STATE", PERFORMED.name())).isFalse();
    }
}
