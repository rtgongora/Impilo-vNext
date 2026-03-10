package zw.gov.mohcc.impilo.channels.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID sessionId,
        String direction,
        String channelType,
        String contentType,
        String deliveryStatus,
        OffsetDateTime createdAt
) {}
