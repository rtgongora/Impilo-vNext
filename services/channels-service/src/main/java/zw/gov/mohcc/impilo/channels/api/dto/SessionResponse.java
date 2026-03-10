package zw.gov.mohcc.impilo.channels.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String channelType,
        String status,
        String subjectRef,
        String agentId,
        String facilityId,
        OffsetDateTime startedAt,
        OffsetDateTime lastActivity,
        OffsetDateTime closedAt
) {}
