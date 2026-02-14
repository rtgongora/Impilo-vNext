package zw.gov.mohcc.impilo.search.api.dto;

import java.time.OffsetDateTime;

public record IndexDefinitionResponse(
        String id,
        String name,
        String sourceType,
        Object fields,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
