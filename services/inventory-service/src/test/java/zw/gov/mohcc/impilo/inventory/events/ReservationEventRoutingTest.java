package zw.gov.mohcc.impilo.inventory.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OF-B11 — both outbox publishers (companion + legacy) must route the
 * reservation lifecycle events identically, including the legacy
 * status-changed topic the msika-flow projection consumer listens on.
 */
class ReservationEventRoutingTest {

    @Test
    void companionPublisher_routesReservationEvents() {
        assertEquals("inventory.reservation.created.v1",
                InventoryOutboxPublisher.routeTopic("RESERVATION_CREATED"));
        assertEquals("inventory.reservation.released.v1",
                InventoryOutboxPublisher.routeTopic("RESERVATION_RELEASED"));
        assertEquals("inventory.reservation.consumed.v1",
                InventoryOutboxPublisher.routeTopic("RESERVATION_CONSUMED"));
        assertEquals("inventory.reservation.expired.v1",
                InventoryOutboxPublisher.routeTopic("RESERVATION_EXPIRED"));
        assertEquals("inventory.reservation.status_changed",
                InventoryOutboxPublisher.routeTopic("RESERVATION_STATUS_CHANGED"));
    }

    @Test
    void legacyPublisher_routesReservationEventsIdentically() {
        assertEquals("inventory.reservation.created.v1",
                OutboxPublisher.routeTopic("RESERVATION_CREATED"));
        assertEquals("inventory.reservation.status_changed",
                OutboxPublisher.routeTopic("RESERVATION_STATUS_CHANGED"));
    }

    @Test
    void unknownEvents_stillFallBackToGenericTopic() {
        assertEquals("inventory.events", InventoryOutboxPublisher.routeTopic("SOMETHING_ELSE"));
        assertEquals("inventory.events", OutboxPublisher.routeTopic(null));
    }
}
