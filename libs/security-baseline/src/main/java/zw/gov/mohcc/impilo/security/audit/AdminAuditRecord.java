package zw.gov.mohcc.impilo.security.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing an admin audit event destined for the outbox.
 *
 * @param eventId       unique event ID
 * @param aggregateType outbox aggregate type (always "AdminAudit")
 * @param aggregateId   the subject of the action
 * @param eventType     dot-notation event type
 * @param payload       serialized EventEnvelope JSON
 * @param createdAt     when the event was created
 */
public record AdminAuditRecord(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant createdAt
) {
    public AdminAuditRecord {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(aggregateType, "aggregateType is required");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
