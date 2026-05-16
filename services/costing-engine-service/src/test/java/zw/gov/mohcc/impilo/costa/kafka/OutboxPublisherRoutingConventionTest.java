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
                "RULESET_PUBLISHED"
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
        assertFalse(OutboxPublisher.shouldEmitCoreTransaction("RULESET_PUBLISHED"));
    }
}
