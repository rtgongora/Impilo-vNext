package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        UUID tenantId,
        Long facilityId,
        UUID workspaceId,
        String workspaceName,
        String providerId,
        String providerType,
        Instant startedAt,
        Instant endedAt,
        String status,
        Map<String, Object> metadata,
        Instant createdAt
) {}
