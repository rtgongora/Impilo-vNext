package zw.gov.mohcc.impilo.integration.api.dto;

import java.time.OffsetDateTime;

public record RouteResponse(
        String id,
        String sourceService,
        String eventTypePrefix,
        String targetService,
        String targetUrl,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
