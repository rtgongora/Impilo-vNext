package zw.gov.mohcc.impilo.search.api.dto;

import java.time.OffsetDateTime;

public record DocumentResponse(
        String id,
        String indexId,
        String externalId,
        String title,
        String bodyText,
        Object metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
