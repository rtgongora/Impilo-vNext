package zw.gov.mohcc.impilo.channels.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String channelType,
        String sessionState,
        String clientId,
        String agentId,
        OffsetDateTime startedAt,
        OffsetDateTime lastActivity,
        OffsetDateTime closedAt
) {}
