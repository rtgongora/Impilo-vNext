package zw.gov.mohcc.impilo.telemonitoring.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus.ACTIVE;
import static zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus.CANCELLED;
import static zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus.COMPLETED;
import static zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus.DRAFT;
import static zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus.PENDING_APPROVAL;
import static zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus.SUSPENDED;

/** Full transition matrix for the §14.3 plan lifecycle machine. */
class PlanStatusMachineTest {

    private static final Map<PlanStatus, Set<PlanStatus>> EXPECTED = Map.of(
            DRAFT, EnumSet.of(PENDING_APPROVAL, CANCELLED),
            PENDING_APPROVAL, EnumSet.of(ACTIVE, CANCELLED),
            ACTIVE, EnumSet.of(SUSPENDED, COMPLETED, CANCELLED),
            SUSPENDED, EnumSet.of(ACTIVE, COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(PlanStatus.class),
            CANCELLED, EnumSet.noneOf(PlanStatus.class));

    @Test
    void fullMatrixMatchesSpec() {
        for (PlanStatus from : PlanStatus.values()) {
            for (PlanStatus to : PlanStatus.values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                assertThat(PlanStatus.canTransition(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void terminalStatesAreExactlyCompletedAndCancelled() {
        assertThat(EnumSet.allOf(PlanStatus.class).stream().filter(PlanStatus::isTerminal))
                .containsExactlyInAnyOrder(COMPLETED, CANCELLED);
    }

    @Test
    void draftCannotJumpStraightToActive() {
        // The approval gate demands DRAFT pass through PENDING_APPROVAL — a machine-level
        // guard against any code path activating a plan without the approval step.
        assertThat(PlanStatus.canTransition(DRAFT, ACTIVE)).isFalse();
    }

    @Test
    void nullsNeverTransition() {
        assertThat(PlanStatus.canTransition(null, ACTIVE)).isFalse();
        assertThat(PlanStatus.canTransition(DRAFT, null)).isFalse();
    }
}
