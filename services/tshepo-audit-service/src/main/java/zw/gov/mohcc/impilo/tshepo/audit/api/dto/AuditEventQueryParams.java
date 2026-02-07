package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Query parameters for filtering audit events.
 */
public record AuditEventQueryParams(
        UUID tenantId,
        String actorId,
        String subjectRef,
        String eventType,
        Instant fromTime,
        Instant toTime,
        int page,
        int size
) {
    public AuditEventQueryParams {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
    }
}
