package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full audit event response for authorized consumers (internal query).
 */
public record AuditEventResponse(
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
        String policyVersion,
        String detail,
        String previousHash,
        String entryHash,
        Long sequenceNumber,
        Instant createdAt
) {
}
