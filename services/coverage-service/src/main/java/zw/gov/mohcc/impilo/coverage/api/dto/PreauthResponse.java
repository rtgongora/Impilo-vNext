package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PreauthResponse(
        UUID id,
        UUID coverageId,
        String facilityId,
        String providerId,
        String requestType,
        String status,
        String requestedItems,
        String decisionJson,
        OffsetDateTime requestedAt,
        OffsetDateTime decidedAt,
        OffsetDateTime expiresAt
) {}
