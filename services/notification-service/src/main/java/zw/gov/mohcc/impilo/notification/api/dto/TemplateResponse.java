package zw.gov.mohcc.impilo.notification.api.dto;

import java.time.OffsetDateTime;

public record TemplateResponse(
        String id,
        String channel,
        String name,
        String content,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
