package zw.gov.mohcc.impilo.oros.events;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutboxPublisherRoutingConventionTest {

    @Test
    void producedEventTypes_routeToDedicatedTopics_notDefaultCatchAll() {
        Set<String> producedTypes = Set.of(
                "ORDER_PLACED",
                "ORDER_DRAFT_CREATED",
                "ORDER_DRAFT_UPDATED",
                "ORDER_STATUS_CHANGED",
                "ORDER_ACCEPTED",
                "ORDER_COMPLETED",
                "ORDER_CANCELLED",
                "RESULT_AVAILABLE",
                "RESULT_POSTED",
                "RESULT_CRITICAL",
                "IMAGING_STATE_RECEIVED",
                "IMAGING_STATE_SCHEDULED",
                "IMAGING_STATE_RELEASED"
        );

        for (String eventType : producedTypes) {
            assertNotEquals("oros.events", OutboxPublisher.routeTopic(eventType),
                    "event type unexpectedly routed to default topic: " + eventType);
        }
    }

    @Test
    void clinicalEvents_dualEmitToCoreTransactionTopic() {
        assertNotEquals(null, OutboxPublisher.resolveCoreTransactionTopic("ORDER_COMPLETED"));
        assertNotEquals(null, OutboxPublisher.resolveCoreTransactionTopic("RESULT_POSTED"));
        assertNull(OutboxPublisher.resolveCoreTransactionTopic("WORKSTEP_STARTED"));
    }
}
