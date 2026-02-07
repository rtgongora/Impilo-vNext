package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit entry for the tamper-evident audit chain.
 *
 * <p>Every authorization decision, token issuance, consent change,
 * and key operation produces an audit entry. Entries are hash-chained
 * (SHA-256) for tamper detection.</p>
 */
public record AuditEntry(
        UUID id,
        UUID tenantId,
        String eventType,
        String actorId,
        String actorType,
        String subjectRef,
        String resourceType,
        String resourceId,
        String action,
        String outcome,
        String purposeOfUse,
        UUID facilityId,
        UUID correlationId,
        String detail,
        String previousHash,
        String entryHash,
        long sequenceNumber,
        Instant timestamp
) {}
