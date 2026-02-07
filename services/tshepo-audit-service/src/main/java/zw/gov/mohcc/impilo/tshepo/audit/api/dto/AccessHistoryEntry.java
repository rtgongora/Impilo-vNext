package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import java.time.Instant;

/**
 * Redacted audit event for patient-visible access history.
 * Strips internal IDs, resource references, and detail payloads.
 * Only exposes: event type, timestamp, actor type, purpose of use, and outcome.
 */
public record AccessHistoryEntry(
        String eventType,
        String actorType,
        String action,
        String outcome,
        String purposeOfUse,
        Instant timestamp
) {
}
