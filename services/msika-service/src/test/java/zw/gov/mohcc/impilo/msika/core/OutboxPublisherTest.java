package zw.gov.mohcc.impilo.msika.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxPublisherTest {

    private final OutboxPublisher publisher = new OutboxPublisher(null, null, null);

    private CompanionOutboxPublisher.OutboxRow rowWithEventType(String eventType) {
        return new CompanionOutboxPublisher.OutboxRow() {
            public Long id() { return 1L; }
            public String aggregateType() { return "TEST"; }
            public String aggregateId() { return "agg-1"; }
            public String eventType() { return eventType; }
            public String payloadJson() { return "{}"; }
            public OffsetDateTime occurredAt() { return OffsetDateTime.now(); }
            public OffsetDateTime publishedAt() { return null; }
        };
    }

    @Test
    void resolveTopic_catalogPublished() {
        assertEquals("msika.core.catalog.published", publisher.resolveLegacyTopic(rowWithEventType("CATALOG_PUBLISHED")));
        assertEquals("msika.core.catalog.published", publisher.resolveLegacyTopic(rowWithEventType("CATALOG_APPROVED")));
    }

    @Test
    void resolveTopic_itemChanged() {
        assertEquals("msika.core.item.changed", publisher.resolveLegacyTopic(rowWithEventType("ITEM_CREATED")));
        assertEquals("msika.core.item.changed", publisher.resolveLegacyTopic(rowWithEventType("ITEM_UPDATED")));
        assertEquals("msika.core.item.changed", publisher.resolveLegacyTopic(rowWithEventType("ITEM_DELETED")));
    }

    @Test
    void resolveTopic_mappingApproved() {
        assertEquals("msika.core.mapping.approved", publisher.resolveLegacyTopic(rowWithEventType("MAPPING_APPROVED")));
    }

    @Test
    void resolveTopic_default() {
        assertEquals("msika.core.events", publisher.resolveLegacyTopic(rowWithEventType("UNKNOWN_EVENT")));
        assertEquals("msika.core.events", publisher.resolveLegacyTopic(rowWithEventType(null)));
    }
}
