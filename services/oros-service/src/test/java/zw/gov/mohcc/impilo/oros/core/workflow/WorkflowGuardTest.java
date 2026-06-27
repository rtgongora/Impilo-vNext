package zw.gov.mohcc.impilo.oros.core.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the per-category fulfilment-workflow guards and the {@link WorkflowGuardRegistry}.
 */
class WorkflowGuardTest {

    private final LabWorkflow lab = new LabWorkflow();
    private final ProcedureWorkflow procedure = new ProcedureWorkflow();
    private final ImagingFulfilmentWorkflow imaging = new ImagingFulfilmentWorkflow();

    @Test
    @DisplayName("lab entry is RECEIVED; null -> non-entry is denied")
    void labEntry() {
        assertThat(lab.canTransition(null, "RECEIVED")).isTrue();
        assertThat(lab.canTransition(null, "COLLECTED")).isFalse();
        assertThat(lab.entryState()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("lab happy path: RECEIVED -> COLLECTED -> RECEIVED_IN_LAB -> IN_PROGRESS -> FINAL_RESULT -> RELEASED")
    void labHappyPath() {
        assertThat(lab.canTransition("RECEIVED", "AWAITING_COLLECTION")).isTrue();
        assertThat(lab.canTransition("AWAITING_COLLECTION", "COLLECTED")).isTrue();
        assertThat(lab.canTransition("COLLECTED", "RECEIVED_IN_LAB")).isTrue();
        assertThat(lab.canTransition("RECEIVED_IN_LAB", "IN_PROGRESS")).isTrue();
        assertThat(lab.canTransition("IN_PROGRESS", "FINAL_RESULT")).isTrue();
        assertThat(lab.canTransition("FINAL_RESULT", "RELEASED")).isTrue();
        assertThat(lab.canTransition("RELEASED", "ACKNOWLEDGED")).isTrue();
        assertThat(lab.canTransition("ACKNOWLEDGED", "CLOSED")).isTrue();
    }

    @Test
    @DisplayName("lab specimen exceptions: reject -> recollect -> awaiting collection")
    void labSpecimenException() {
        assertThat(lab.canTransition("COLLECTED", "SPECIMEN_REJECTED")).isTrue();
        assertThat(lab.canTransition("SPECIMEN_REJECTED", "RECOLLECT_REQUESTED")).isTrue();
        assertThat(lab.canTransition("RECOLLECT_REQUESTED", "AWAITING_COLLECTION")).isTrue();
    }

    @Test
    @DisplayName("lab illegal skip and terminal transitions are denied")
    void labDenies() {
        assertThat(lab.canTransition("RECEIVED", "FINAL_RESULT")).isFalse();
        assertThat(lab.canTransition("CLOSED", "RELEASED")).isFalse();
        assertThat(lab.canTransition("RELEASED", "BOGUS")).isFalse();
        assertThatThrownBy(() -> lab.require("RECEIVED", "RELEASED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAB");
    }

    @Test
    @DisplayName("procedure happy path and no-show exception")
    void procedurePath() {
        assertThat(procedure.canTransition(null, "RECEIVED")).isTrue();
        assertThat(procedure.canTransition("RECEIVED", "ACCEPTED")).isTrue();
        assertThat(procedure.canTransition("ACCEPTED", "SCHEDULED")).isTrue();
        assertThat(procedure.canTransition("SCHEDULED", "NO_SHOW")).isTrue();
        assertThat(procedure.canTransition("NO_SHOW", "SCHEDULED")).isTrue();
        assertThat(procedure.canTransition("PERFORMED", "FINAL_REPORT")).isTrue();
        assertThat(procedure.canTransition("FINAL_REPORT", "RELEASED")).isTrue();
        // Labs do not "schedule" via this guard, procedures do not "collect specimens".
        assertThat(procedure.canTransition("RECEIVED", "COLLECTED")).isFalse();
    }

    @Test
    @DisplayName("imaging adapter delegates to the canonical static guard")
    void imagingAdapter() {
        assertThat(imaging.orderType()).isEqualTo(OrderType.IMAGING);
        assertThat(imaging.canTransition(null, "RECEIVED")).isTrue();
        assertThat(imaging.canTransition("PERFORMED", "IMAGES_LINKED")).isTrue();
        assertThat(imaging.canTransition("RECEIVED", "FINAL_REPORT")).isFalse();
    }

    @Test
    @DisplayName("registry resolves a guard per category; pharmacy/other resolve empty")
    void registry() {
        WorkflowGuardRegistry registry = new WorkflowGuardRegistry(List.of(imaging, lab, procedure));
        assertThat(registry.forType(OrderType.IMAGING)).containsSame(imaging);
        assertThat(registry.forType(OrderType.LAB)).containsSame(lab);
        assertThat(registry.forType(OrderType.PROCEDURE)).containsSame(procedure);
        assertThat(registry.hasGuard(OrderType.PHARMACY)).isFalse();
        assertThat(registry.forType(OrderType.OTHER)).isEmpty();
        assertThatThrownBy(() -> registry.require(OrderType.PHARMACY))
                .isInstanceOf(IllegalStateException.class);
    }
}
