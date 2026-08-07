package zw.gov.mohcc.impilo.referral.events;

import zw.gov.mohcc.impilo.referral.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;

/**
 * Adapts a referral outbox row to the publisher contract.
 *
 * <p>One event type is not enough here. The stored {@code event_type} is the legacy value —
 * {@code referral.surgical.accepted} for the wire-contract events and {@code REFERRAL_CREATED}
 * for the internal lifecycle ones — and it is also the legacy Kafka topic name. The v1.1
 * envelope, however, requires {@code impilo.{service}.{entity}.{action}.v{N}}; feeding the
 * legacy value straight through produces an envelope that fails the event-type contract.</p>
 *
 * <p>So this row exposes both: {@link #eventType()} is the canonical form the envelope
 * carries, and {@link #legacyEventType()} is the stored value the legacy topic is named
 * after. Nothing is invented — the canonical form is derived from the aggregate type and
 * the action already present in the stored value.</p>
 */
public final class ReferralOutboxRow implements CompanionOutboxPublisher.OutboxRow {

    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("referral");

    private final EventOutboxEntity entity;

    public ReferralOutboxRow(EventOutboxEntity entity) {
        this.entity = entity;
    }

    /** The stored event type, which doubles as the legacy topic name. */
    public String legacyEventType() {
        return entity.getEventType();
    }

    /**
     * Legacy lifecycle events are internal — the pre-conversion publisher emitted only
     * dotted event types to Kafka and kept UPPER_SNAKE ones in the outbox. That boundary is
     * preserved so no new legacy traffic appears on the estate.
     */
    public boolean hasLegacyTopic() {
        String legacy = legacyEventType();
        return legacy != null && legacy.contains(".");
    }

    @Override
    public String eventType() {
        return REGISTRY.eventType(entityName(), action());
    }

    private String entityName() {
        String aggregate = entity.getAggregateType();
        if (aggregate == null) {
            return "referral";
        }
        return switch (aggregate) {
            case "SurgicalReferral" -> "surgical_referral";
            case "Referral" -> "referral";
            default -> aggregate.toLowerCase();
        };
    }

    /** The trailing segment of the stored event type: {@code REFERRAL_ACCEPTED} → accepted. */
    private String action() {
        String legacy = legacyEventType();
        if (legacy == null || legacy.isBlank()) {
            return "changed";
        }
        int lastSeparator = Math.max(legacy.lastIndexOf('.'), legacy.lastIndexOf('_'));
        String tail = lastSeparator >= 0 && lastSeparator < legacy.length() - 1
                ? legacy.substring(lastSeparator + 1)
                : legacy;
        return tail.toLowerCase();
    }

    @Override public Long id() { return entity.getId(); }
    @Override public String aggregateType() { return entity.getAggregateType(); }
    @Override public String aggregateId() { return entity.getAggregateId(); }
    @Override public String payloadJson() { return entity.getPayloadJson(); }
    @Override public OffsetDateTime occurredAt() { return entity.getOccurredAt(); }
    @Override public OffsetDateTime publishedAt() { return entity.getPublishedAt(); }
    @Override public String tenantId() {
        return entity.getTenantId() != null ? entity.getTenantId().toString() : null;
    }
    @Override public String podId() { return entity.getPodId(); }
    @Override public String correlationId() { return entity.getCorrelationId(); }
    @Override public String causationId() { return entity.getCausationId(); }
    @Override public String idempotencyKey() { return entity.getIdempotencyKey(); }
    @Override public int schemaVersion() { return entity.getSchemaVersion(); }
    @Override public String subjectType() { return entity.getSubjectType(); }
    @Override public String subjectId() { return entity.getSubjectId(); }
    @Override public String partitionKey() { return entity.getAggregateId(); }
    @Override public int retryCount() { return entity.getRetryCount(); }
}
