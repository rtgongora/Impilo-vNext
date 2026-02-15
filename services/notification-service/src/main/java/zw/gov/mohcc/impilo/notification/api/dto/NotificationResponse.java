package zw.gov.mohcc.impilo.notification.api.dto;

import java.time.OffsetDateTime;

public record NotificationResponse(
        String id,
        String templateKey,
        String channel,
        String to,
        String status,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt
) {
}
