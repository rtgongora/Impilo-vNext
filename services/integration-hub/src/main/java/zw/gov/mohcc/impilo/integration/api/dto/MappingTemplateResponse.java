package zw.gov.mohcc.impilo.integration.api.dto;

import java.time.OffsetDateTime;

public record MappingTemplateResponse(
        String id,
        String name,
        String tenantId,
        String podId,
        String sourceFormat,
        String targetFormat,
        String mappingRulesJson,
        boolean active,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
