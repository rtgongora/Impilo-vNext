package zw.gov.mohcc.impilo.forms.api.dto;

import java.time.OffsetDateTime;

public record FormDefinitionResponse(
        String id,
        String tenantId,
        String code,
        String name,
        String description,
        String category,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
