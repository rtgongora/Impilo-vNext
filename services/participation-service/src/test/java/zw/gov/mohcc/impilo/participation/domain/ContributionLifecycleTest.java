package zw.gov.mohcc.impilo.participation.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the contribution state machine that {@code ContributionService.transition} enforces.
 * These are the guard rails that stop a contribution from silently skipping the co-design cycle.
 */
class ContributionLifecycleTest {

    @Test
    void happyPathWalkThroughEveryStageIsLegal() {
        String[] cycle = {
                ContributionLifecycle.RECEIVED,
                ContributionLifecycle.UNDER_COMMUNITY_REVIEW,
                ContributionLifecycle.BEING_EXPLORED,
                ContributionLifecycle.IN_RESEARCH,
                ContributionLifecycle.SELECTED_FOR_CODESIGN,
                ContributionLifecycle.PLANNED,
                ContributionLifecycle.IN_DEVELOPMENT,
                ContributionLifecycle.READY_FOR_TESTING,
                ContributionLifecycle.RELEASED,
        };
        for (int i = 0; i < cycle.length - 1; i++) {
            assertTrue(ContributionLifecycle.canTransition(cycle[i], cycle[i + 1]),
                    cycle[i] + " -> " + cycle[i + 1] + " should be legal");
        }
    }

    @Test
    void illegalStageJumpsAreRejected() {
        assertFalse(ContributionLifecycle.canTransition(
                ContributionLifecycle.RECEIVED, ContributionLifecycle.RELEASED));
        assertFalse(ContributionLifecycle.canTransition(
                ContributionLifecycle.RECEIVED, ContributionLifecycle.IN_DEVELOPMENT));
        assertFalse(ContributionLifecycle.canTransition(
                ContributionLifecycle.PLANNED, ContributionLifecycle.RECEIVED));
    }

    @Test
    void offRampsAreReachableFromAnyWorkingStatus() {
        assertTrue(ContributionLifecycle.canTransition(
                ContributionLifecycle.UNDER_COMMUNITY_REVIEW, ContributionLifecycle.NOT_FEASIBLE));
        assertTrue(ContributionLifecycle.canTransition(
                ContributionLifecycle.IN_DEVELOPMENT, ContributionLifecycle.MERGED));
    }

    @Test
    void notFeasibleCanBeReopenedButTerminalStatusesAreClosed() {
        assertTrue(ContributionLifecycle.canTransition(
                ContributionLifecycle.NOT_FEASIBLE, ContributionLifecycle.UNDER_COMMUNITY_REVIEW));
        assertFalse(ContributionLifecycle.canTransition(
                ContributionLifecycle.RELEASED, ContributionLifecycle.UNDER_COMMUNITY_REVIEW));
        assertFalse(ContributionLifecycle.canTransition(
                ContributionLifecycle.MERGED, ContributionLifecycle.UNDER_COMMUNITY_REVIEW));
    }

    @Test
    void idempotentSelfTransitionIsAllowedButNullsAreNot() {
        assertTrue(ContributionLifecycle.canTransition(
                ContributionLifecycle.PLANNED, ContributionLifecycle.PLANNED));
        assertFalse(ContributionLifecycle.canTransition(null, ContributionLifecycle.PLANNED));
        assertFalse(ContributionLifecycle.canTransition(ContributionLifecycle.PLANNED, null));
    }

    @Test
    void unknownStatusesAreNotRecognised() {
        assertTrue(ContributionLifecycle.isKnown(ContributionLifecycle.RECEIVED));
        assertFalse(ContributionLifecycle.isKnown("SOMETHING_ELSE"));
    }

    @Test
    void publicTypesAreARestrictedSubsetOfKnownTypes() {
        assertTrue(ContributionType.PUBLIC_TYPES.contains(ContributionType.IDEA));
        assertTrue(ContributionType.PUBLIC_TYPES.contains(ContributionType.EXPERIENCE));
        // Testing / community-of-practice are NOT openable through the anonymous public lane.
        assertFalse(ContributionType.PUBLIC_TYPES.contains(ContributionType.TESTING_SIGNUP));
        assertFalse(ContributionType.PUBLIC_TYPES.contains(ContributionType.COP_MEMBERSHIP));
        assertTrue(ContributionType.isKnown(ContributionType.COP_MEMBERSHIP));
        assertFalse(ContributionType.isKnown("MYSTERY"));
    }

    @Test
    void moderationStatesGateTheBoard() {
        assertTrue(ModerationState.isKnown(ModerationState.APPROVED));
        assertFalse(ModerationState.isKnown("VISIBLE"));
    }
}
