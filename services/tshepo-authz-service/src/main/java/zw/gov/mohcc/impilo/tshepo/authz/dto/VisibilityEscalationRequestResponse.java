package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.time.Instant;
import java.util.UUID;

public record VisibilityEscalationRequestResponse(
        long id,
        UUID tenantId,
        String actorId,
        String workflowType,
        String justification,
        String requestedVisibilityCeiling,
        String status,
        String reviewedBy,
        String reviewNotes,
        Instant createdAt
) {}
