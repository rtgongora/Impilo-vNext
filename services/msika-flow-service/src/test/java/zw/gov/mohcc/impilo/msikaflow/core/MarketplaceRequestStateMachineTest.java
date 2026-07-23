package zw.gov.mohcc.impilo.msikaflow.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;

import static org.junit.jupiter.api.Assertions.*;
import static zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus.*;

/**
 * §9.3 request-machine transition matrix (OF-B4).
 */
class MarketplaceRequestStateMachineTest {

    @Test
    void draft_canPublishOrCancel_only() {
        assertTrue(MarketplaceRequestStateMachine.canTransition(DRAFT, PUBLISHED));
        assertTrue(MarketplaceRequestStateMachine.canTransition(DRAFT, CANCELLED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(DRAFT, COMMITTED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(DRAFT, OFFERS_AVAILABLE));
        assertFalse(MarketplaceRequestStateMachine.canTransition(DRAFT, EXPIRED));
    }

    @Test
    void published_flowsToCollecting() {
        assertTrue(MarketplaceRequestStateMachine.canTransition(PUBLISHED, COLLECTING_OFFERS));
        assertTrue(MarketplaceRequestStateMachine.canTransition(PUBLISHED, FAILED_NO_OFFERS));
        assertFalse(MarketplaceRequestStateMachine.canTransition(PUBLISHED, COMMITTED));
    }

    @Test
    void collecting_toOffersAvailable_orFailedNoOffers() {
        assertTrue(MarketplaceRequestStateMachine.canTransition(COLLECTING_OFFERS, OFFERS_AVAILABLE));
        assertTrue(MarketplaceRequestStateMachine.canTransition(COLLECTING_OFFERS, FAILED_NO_OFFERS));
        assertTrue(MarketplaceRequestStateMachine.canTransition(COLLECTING_OFFERS, CANCELLED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(COLLECTING_OFFERS, COMMITTED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(COLLECTING_OFFERS, SELECTION_PENDING));
    }

    @Test
    void offersAvailable_selectionAndCommitPaths() {
        assertTrue(MarketplaceRequestStateMachine.canTransition(OFFERS_AVAILABLE, SELECTION_PENDING));
        assertTrue(MarketplaceRequestStateMachine.canTransition(OFFERS_AVAILABLE, COMMITTED));
        assertTrue(MarketplaceRequestStateMachine.canTransition(OFFERS_AVAILABLE, EXPIRED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(OFFERS_AVAILABLE, PUBLISHED));
    }

    @Test
    void selectionPending_revalidationFailureReturnsToOffersAvailable() {
        // RC-2/3/6/7: honest re-offer, never a silent hold
        assertTrue(MarketplaceRequestStateMachine.canTransition(SELECTION_PENDING, OFFERS_AVAILABLE));
        assertTrue(MarketplaceRequestStateMachine.canTransition(SELECTION_PENDING, COMMITTED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(SELECTION_PENDING, COLLECTING_OFFERS));
    }

    @Test
    void failedNoOffers_canRepublish() {
        assertTrue(MarketplaceRequestStateMachine.canTransition(FAILED_NO_OFFERS, PUBLISHED));
        assertTrue(MarketplaceRequestStateMachine.canTransition(FAILED_NO_OFFERS, CANCELLED));
        assertFalse(MarketplaceRequestStateMachine.canTransition(FAILED_NO_OFFERS, OFFERS_AVAILABLE));
    }

    @Test
    void terminals_haveNoExits() {
        for (MarketplaceRequestStatus terminal : new MarketplaceRequestStatus[]{
                NOT_REQUIRED, COMMITTED, CANCELLED, EXPIRED}) {
            assertTrue(MarketplaceRequestStateMachine.isTerminal(terminal), terminal + " must be terminal");
            for (MarketplaceRequestStatus to : MarketplaceRequestStatus.values()) {
                assertFalse(MarketplaceRequestStateMachine.canTransition(terminal, to),
                        terminal + " -> " + to + " must be illegal");
            }
        }
    }

    @Test
    void assertTransition_throwsOnIllegalMove() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MarketplaceRequestStateMachine.assertTransition(COMMITTED, CANCELLED));
        assertTrue(ex.getMessage().contains("COMMITTED"));
    }
}
