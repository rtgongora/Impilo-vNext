package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTopicRegistryTest {

    private final EventTopicRegistry registry = new EventTopicRegistry("vito");

    @Test
    void eventTypeFollowsConvention() {
        assertEquals("impilo.vito.client.created.v1", registry.eventType("client", "created"));
        assertEquals("impilo.vito.client.updated.v2", registry.eventType("client", "updated", 2));
    }

    @Test
    void snapshotEventTypeFollowsConvention() {
        assertEquals("impilo.vito.snapshot.client.v1", registry.snapshotEventType("client"));
        assertEquals("impilo.vito.snapshot.client.v2", registry.snapshotEventType("client", 2));
    }

    @Test
    void v11TopicFollowsConvention() {
        assertEquals("impilo.vito.identity", registry.v11Topic("identity"));
        assertEquals("impilo.vito.cards", registry.v11Topic("cards"));
    }

    @Test
    void snapshotTopicFollowsConvention() {
        assertEquals("impilo.vito.snapshots", registry.snapshotTopic());
    }

    @Test
    void normalizesInput() {
        assertEquals("impilo.vito.smart_card.created.v1", registry.eventType("SMART_CARD", "CREATED"));
        assertEquals("impilo.vito.smart_card.created.v1", registry.eventType("Smart-Card", "Created"));
    }

    @Test
    void validatesEventType() {
        assertTrue(registry.isValidEventType("impilo.vito.client.created.v1"));
        assertTrue(registry.isValidEventType("impilo.vito.snapshot.client.v1"));
        assertFalse(registry.isValidEventType("impilo.tuso.client.created.v1")); // wrong service
        assertFalse(registry.isValidEventType(null));
        assertFalse(registry.isValidEventType("impilo.vito")); // too short
        assertFalse(registry.isValidEventType("random.string"));
    }

    @Test
    void extractsEntity() {
        assertEquals("client", registry.extractEntity("impilo.vito.client.created.v1"));
        assertEquals("client", registry.extractEntity("impilo.vito.snapshot.client.v1"));
        assertNull(registry.extractEntity("impilo.tuso.client.created.v1")); // wrong service
        assertNull(registry.extractEntity(null));
    }

    @Test
    void extractsAction() {
        assertEquals("created", registry.extractAction("impilo.vito.client.created.v1"));
        assertEquals("updated", registry.extractAction("impilo.vito.client.updated.v2"));
        assertNull(registry.extractAction(null));
    }

    @Test
    void serviceIdAccessor() {
        assertEquals("vito", registry.serviceId());
    }

    @Test
    void differentServiceIds() {
        var tusoRegistry = new EventTopicRegistry("tuso");
        assertEquals("impilo.tuso.facility.created.v1", tusoRegistry.eventType("facility", "created"));
        assertEquals("impilo.tuso.snapshots", tusoRegistry.snapshotTopic());
    }
}
