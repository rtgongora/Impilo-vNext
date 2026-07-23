package zw.gov.mohcc.impilo.pharmacy.events;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutboxPublisherRoutingConventionTest {

    @Test
    void producedEventTypes_routeToDedicatedTopics_notDefaultCatchAll() {
        Set<String> producedTypes = Set.of(
                "PRESCRIPTION_CREATED",
                "DISPENSE_ORDER_RECEIVED",
                "DISPENSE_ACCEPTED",
                "DISPENSE_COMPLETED",
                "DISPENSE_CANCELLED",
                "PICKUP_CLAIMED",
                "MUSHEX_CHARGE_REQUESTED",
                // DISPENSE lane (OF-B12/B15/B16)
                "DISPENSE_CLAIM_ANOMALY",
                "DISPENSE_CLARIFICATION_REQUESTED",
                "DISPENSE_CLARIFICATION_RESOLVED",
                "PICKUP_EXPIRED_RETURN"
        );

        for (String eventType : producedTypes) {
            assertNotEquals("pharmacy.events", OutboxPublisher.routeTopic(eventType),
                    "event type unexpectedly routed to default topic: " + eventType);
        }
    }

    @Test
    void dispenseLaneEventTypes_routeToLockedTopics() {
        org.junit.jupiter.api.Assertions.assertEquals("pharmacy.dispense.clarification.v1",
                OutboxPublisher.routeTopic("DISPENSE_CLARIFICATION_REQUESTED"));
        org.junit.jupiter.api.Assertions.assertEquals("pharmacy.dispense.clarification.v1",
                OutboxPublisher.routeTopic("DISPENSE_CLARIFICATION_RESOLVED"));
        org.junit.jupiter.api.Assertions.assertEquals("pharmacy.dispense.claim.anomaly.v1",
                OutboxPublisher.routeTopic("DISPENSE_CLAIM_ANOMALY"));
        org.junit.jupiter.api.Assertions.assertEquals("pharmacy.pickup.expired",
                OutboxPublisher.routeTopic("PICKUP_EXPIRED_RETURN"));
    }

    @Test
    void clinicalAndFinancialEvents_dualEmitToCoreTransactionTopic() {
        assertNotEquals(null, OutboxPublisher.resolveCoreTransactionTopic("DISPENSE_COMPLETED"));
        assertNotEquals(null, OutboxPublisher.resolveCoreTransactionTopic("MUSHEX_CHARGE_REQUESTED"));
        assertNull(OutboxPublisher.resolveCoreTransactionTopic("RETURN_RECORDED"));
    }
}
