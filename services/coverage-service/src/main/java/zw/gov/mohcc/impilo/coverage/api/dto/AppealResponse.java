package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppealResponse(
        UUID id,
        UUID tenantId,
        String claimId,
        UUID coverageId,
        String appellantType,
        String appellantId,
        String reason,
        String evidenceJson,
        String status,
        String decision,
        String decisionReason,
        String reviewerId,
        String decidedBy,
        OffsetDateTime decidedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
