package zw.gov.mohcc.impilo.notification.api.dto;

import java.time.OffsetDateTime;

public record TemplateResponse(
        String id,
        String key,
        String channel,
        String subject,
        String body,
        boolean enabled,
        String status,
        int currentVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
