package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.time.OffsetDateTime;

/**
 * Thin read-only projection over {@code mf_ops_reviews} (review decisions) and
 * {@code mf_event_outbox} (order/vendor/refund lifecycle events) — a unified ops
 * audit view with no new table.
 */
public record AuditEntryView(
        String id,
        String category,
        String entityType,
        String entityRef,
        String summary,
        String actorRef,
        OffsetDateTime occurredAt
) {}
