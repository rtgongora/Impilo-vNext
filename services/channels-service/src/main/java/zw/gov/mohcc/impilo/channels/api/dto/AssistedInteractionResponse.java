package zw.gov.mohcc.impilo.channels.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssistedInteractionResponse(
        UUID id,
        UUID sessionId,
        String agentId,
        String clientId,
        OffsetDateTime createdAt
) {}
