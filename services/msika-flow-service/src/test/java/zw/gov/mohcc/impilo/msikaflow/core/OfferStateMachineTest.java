package zw.gov.mohcc.impilo.msikaflow.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;

import static org.junit.jupiter.api.Assertions.*;
import static zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus.*;

/**
 * §9.4 offer-machine transition matrix (OF-B6).
 */
class OfferStateMachineTest {

    @Test
    void submissionPath() {
        assertTrue(OfferStateMachine.canTransition(DRAFT, SUBMITTED));
        assertTrue(OfferStateMachine.canTransition(SUBMITTED, ACTIVE));
        assertFalse(OfferStateMachine.canTransition(DRAFT, ACTIVE));
        assertFalse(OfferStateMachine.canTransition(SUBMITTED, COMMITTED));
    }

    @Test
    void active_selectWithdrawExpire() {
        assertTrue(OfferStateMachine.canTransition(ACTIVE, SELECTED));
        assertTrue(OfferStateMachine.canTransition(ACTIVE, WITHDRAWN));
        assertTrue(OfferStateMachine.canTransition(ACTIVE, EXPIRED));
        assertTrue(OfferStateMachine.canTransition(ACTIVE, NOT_SELECTED));
        assertFalse(OfferStateMachine.canTransition(ACTIVE, COMMITTED));
        assertFalse(OfferStateMachine.canTransition(ACTIVE, REVALIDATING));
    }

    @Test
    void selected_cannotBeWithdrawn_whileInCommit() {
        // §9.4 guard: withdrawal is not legal while SELECTED-in-commit
        assertFalse(OfferStateMachine.canTransition(SELECTED, WITHDRAWN));
        assertTrue(OfferStateMachine.canTransition(SELECTED, REVALIDATING));
        assertTrue(OfferStateMachine.canTransition(SELECTED, FAILED_REVALIDATION));
        assertTrue(OfferStateMachine.canTransition(SELECTED, EXPIRED));
    }

    @Test
    void revalidating_commitsOrFailsOnly() {
        assertTrue(OfferStateMachine.canTransition(REVALIDATING, COMMITTED));
        assertTrue(OfferStateMachine.canTransition(REVALIDATING, FAILED_REVALIDATION));
        assertFalse(OfferStateMachine.canTransition(REVALIDATING, ACTIVE));
        assertFalse(OfferStateMachine.canTransition(REVALIDATING, WITHDRAWN));
        assertFalse(OfferStateMachine.canTransition(REVALIDATING, EXPIRED));
    }

    @Test
    void terminals_haveNoExits() {
        for (OfferStatus terminal : new OfferStatus[]{
                COMMITTED, NOT_SELECTED, WITHDRAWN, EXPIRED, FAILED_REVALIDATION}) {
            assertTrue(OfferStateMachine.isTerminal(terminal), terminal + " must be terminal");
            for (OfferStatus to : OfferStatus.values()) {
                assertFalse(OfferStateMachine.canTransition(terminal, to),
                        terminal + " -> " + to + " must be illegal");
            }
        }
    }

    @Test
    void assertTransition_throwsOnIllegalMove() {
        assertThrows(IllegalStateException.class,
                () -> OfferStateMachine.assertTransition(EXPIRED, ACTIVE));
    }
}
