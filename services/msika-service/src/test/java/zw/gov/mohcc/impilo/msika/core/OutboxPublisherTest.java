package zw.gov.mohcc.impilo.msika.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxPublisherTest {

    @Test
    void resolveTopic_catalogPublished() {
        OutboxPublisher publisher = new OutboxPublisher(null, null);
        assertEquals("msika.core.catalog.published", publisher.resolveTopic("CATALOG_PUBLISHED"));
        assertEquals("msika.core.catalog.published", publisher.resolveTopic("CATALOG_APPROVED"));
    }

    @Test
    void resolveTopic_itemChanged() {
        OutboxPublisher publisher = new OutboxPublisher(null, null);
        assertEquals("msika.core.item.changed", publisher.resolveTopic("ITEM_CREATED"));
        assertEquals("msika.core.item.changed", publisher.resolveTopic("ITEM_UPDATED"));
        assertEquals("msika.core.item.changed", publisher.resolveTopic("ITEM_DELETED"));
    }

    @Test
    void resolveTopic_mappingApproved() {
        OutboxPublisher publisher = new OutboxPublisher(null, null);
        assertEquals("msika.core.mapping.approved", publisher.resolveTopic("MAPPING_APPROVED"));
    }

    @Test
    void resolveTopic_default() {
        OutboxPublisher publisher = new OutboxPublisher(null, null);
        assertEquals("msika.core.events", publisher.resolveTopic("UNKNOWN_EVENT"));
        assertEquals("msika.core.events", publisher.resolveTopic(null));
    }
}
