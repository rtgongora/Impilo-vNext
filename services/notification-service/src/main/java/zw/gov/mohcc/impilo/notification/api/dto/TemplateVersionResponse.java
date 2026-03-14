package zw.gov.mohcc.impilo.notification.api.dto;

import java.time.OffsetDateTime;

public record TemplateVersionResponse(
        String id,
        String templateId,
        int version,
        String content,
        String subject,
        String changelog,
        OffsetDateTime createdAt
) {
}
