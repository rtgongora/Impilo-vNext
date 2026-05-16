package zw.gov.mohcc.impilo.msikaflow.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboxPublisherTest {

    @Test
    void routeTopic_orderCreated_mapsCorrectly() {
        assertEquals("msika.flow.order.created", OutboxPublisher.routeTopic("ORDER_CREATED"));
    }

    @Test
    void routeTopic_orderPaid_mapsCorrectly() {
        assertEquals("msika.flow.order.paid", OutboxPublisher.routeTopic("ORDER_PAID"));
    }

    @Test
    void routeTopic_pickupIssued_mapsCorrectly() {
        assertEquals("msika.flow.pickup.issued", OutboxPublisher.routeTopic("PICKUP_ISSUED"));
    }

    @Test
    void routeTopic_pickupClaimed_mapsCorrectly() {
        assertEquals("msika.flow.pickup.claimed", OutboxPublisher.routeTopic("PICKUP_CLAIMED"));
    }

    @Test
    void routeTopic_vendorApplied_mapsCorrectly() {
        assertEquals("msika.flow.vendor.applied", OutboxPublisher.routeTopic("VENDOR_APPLIED"));
    }

    @Test
    void routeTopic_refundRequested_mapsCorrectly() {
        assertEquals("msika.flow.refund.requested", OutboxPublisher.routeTopic("REFUND_REQUESTED"));
    }

    @Test
    void routeTopic_refundCompleted_mapsCorrectly() {
        assertEquals("msika.flow.refund.completed", OutboxPublisher.routeTopic("REFUND_COMPLETED"));
    }

    @Test
    void routeTopic_unknownEvent_fallsBackToGeneric() {
        assertEquals("msika.flow.events", OutboxPublisher.routeTopic("SOME_UNKNOWN_EVENT"));
    }

    @Test
    void routeTopic_nullEvent_fallsBackToGeneric() {
        assertEquals("msika.flow.events", OutboxPublisher.routeTopic(null));
    }

    @Test
    void resolveCoreTransactionTopic_orderCompleted_mapsToCoreTransactionEvents() {
        assertEquals("core.transaction.events", OutboxPublisher.resolveCoreTransactionTopic("ORDER_COMPLETED"));
    }

    @Test
    void resolveCoreTransactionTopic_unknownEvent_returnsNull() {
        assertNull(OutboxPublisher.resolveCoreTransactionTopic("VENDOR_APPROVED"));
    }
}
