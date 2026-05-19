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
                "MUSHEX_CHARGE_REQUESTED"
        );

        for (String eventType : producedTypes) {
            assertNotEquals("pharmacy.events", OutboxPublisher.routeTopic(eventType),
                    "event type unexpectedly routed to default topic: " + eventType);
        }
    }

    @Test
    void clinicalAndFinancialEvents_dualEmitToCoreTransactionTopic() {
        assertNotEquals(null, OutboxPublisher.resolveCoreTransactionTopic("DISPENSE_COMPLETED"));
        assertNotEquals(null, OutboxPublisher.resolveCoreTransactionTopic("MUSHEX_CHARGE_REQUESTED"));
        assertNull(OutboxPublisher.resolveCoreTransactionTopic("RETURN_RECORDED"));
    }
}
