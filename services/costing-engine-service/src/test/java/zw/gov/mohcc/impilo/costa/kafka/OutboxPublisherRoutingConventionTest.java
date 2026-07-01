package zw.gov.mohcc.impilo.costa.kafka;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxPublisherRoutingConventionTest {

    @Test
    void producedEventTypes_routeToDedicatedTopics_notDefaultCatchAll() {
        Set<String> producedTypes = Set.of(
                "BILL_DRAFT_CREATED",
                "BILL_APPROVAL_REQUESTED",
                "BILL_APPROVED",
                "BILL_FINALIZED",
                "BILL_VOIDED",
                "INVOICE_ISSUED",
                "PAYMENT_CANCELLED",
                "PAYMENT_INTENT_CREATED",
                "PAYMENT_STATUS_CHANGED",
                "REFUND_CREATED",
                "CLAIM_PACK_CREATED",
                "EMERGENCY_DEFERRED_CHARGE",
                "RULESET_PUBLISHED",
                "BUDGET_CREATED",
                "BUDGET_SUBMITTED",
                "BUDGET_APPROVED",
                "BUDGET_ACTIVATED",
                "BUDGET_REVISED",
                "BUDGET_FROZEN",
                "BUDGET_CLOSED",
                "BUDGET_COMMITMENT_RECORDED",
                "BUDGET_COMMITMENT_LIQUIDATED",
                "BUDGET_ACTUAL_POSTED",
                "BUDGET_AVAILABILITY_OVERRIDE",
                "BUDGET_THRESHOLD_BREACHED",
                "BUDGET_RECOMMENDATION_RAISED",
                "BUDGET_RECON_EXCEPTION_OPENED",
                "BUDGET_PERIOD_CLOSED"
        );

        for (String eventType : producedTypes) {
            assertNotEquals("costa.events", OutboxPublisher.routeTopic(eventType),
                    "event type unexpectedly routed to default topic: " + eventType);
        }
    }

    @Test
    void financialLifecycleEvents_dualEmitToCoreTransactionTopic() {
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("BILL_FINALIZED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("PAYMENT_STATUS_CHANGED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("CLAIM_PACK_CREATED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("EMERGENCY_DEFERRED_CHARGE"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("WAIVER_APPLIED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("CHARGE_CREATED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("BUDGET_ACTUAL_POSTED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("BUDGET_COMMITMENT_RECORDED"));
        assertTrue(OutboxPublisher.shouldEmitCoreTransaction("BUDGET_AVAILABILITY_OVERRIDE"));
        assertFalse(OutboxPublisher.shouldEmitCoreTransaction("RULESET_PUBLISHED"));
        assertFalse(OutboxPublisher.shouldEmitCoreTransaction("BUDGET_CREATED"));
    }
}
