package zw.gov.mohcc.impilo.costa.domain;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetApprovalAction;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;

import static org.junit.jupiter.api.Assertions.*;

class BudgetLifecycleTest {

    @Test
    void happyPath_draftToActive() {
        assertEquals(BudgetStatus.SUBMITTED, BudgetLifecycle.require(BudgetStatus.DRAFT, BudgetApprovalAction.SUBMIT));
        assertEquals(BudgetStatus.UNDER_REVIEW, BudgetLifecycle.require(BudgetStatus.SUBMITTED, BudgetApprovalAction.REVIEW));
        assertEquals(BudgetStatus.APPROVED, BudgetLifecycle.require(BudgetStatus.UNDER_REVIEW, BudgetApprovalAction.APPROVE));
        assertEquals(BudgetStatus.ACTIVE, BudgetLifecycle.require(BudgetStatus.APPROVED, BudgetApprovalAction.ACTIVATE));
    }

    @Test
    void freezeAndUnfreeze() {
        assertEquals(BudgetStatus.FROZEN, BudgetLifecycle.require(BudgetStatus.ACTIVE, BudgetApprovalAction.FREEZE));
        assertEquals(BudgetStatus.ACTIVE, BudgetLifecycle.require(BudgetStatus.FROZEN, BudgetApprovalAction.ACTIVATE));
    }

    @Test
    void closeAndArchive() {
        assertEquals(BudgetStatus.CLOSED, BudgetLifecycle.require(BudgetStatus.ACTIVE, BudgetApprovalAction.CLOSE));
        assertEquals(BudgetStatus.ARCHIVED, BudgetLifecycle.require(BudgetStatus.CLOSED, BudgetApprovalAction.ARCHIVE));
    }

    @Test
    void rejectAndReturnLoopBackToDraft() {
        assertEquals(BudgetStatus.DRAFT, BudgetLifecycle.require(BudgetStatus.UNDER_REVIEW, BudgetApprovalAction.REJECT));
        assertEquals(BudgetStatus.DRAFT, BudgetLifecycle.require(BudgetStatus.SUBMITTED, BudgetApprovalAction.RETURN));
    }

    @Test
    void illegalTransitionsRejected() {
        assertFalse(BudgetLifecycle.isLegal(BudgetStatus.DRAFT, BudgetApprovalAction.APPROVE));
        assertFalse(BudgetLifecycle.isLegal(BudgetStatus.ACTIVE, BudgetApprovalAction.SUBMIT));
        assertFalse(BudgetLifecycle.isLegal(BudgetStatus.CLOSED, BudgetApprovalAction.ACTIVATE));
        assertNull(BudgetLifecycle.resolve(BudgetStatus.DRAFT, BudgetApprovalAction.APPROVE));
        assertThrows(IllegalStateException.class,
                () -> BudgetLifecycle.require(BudgetStatus.DRAFT, BudgetApprovalAction.APPROVE));
    }
}
