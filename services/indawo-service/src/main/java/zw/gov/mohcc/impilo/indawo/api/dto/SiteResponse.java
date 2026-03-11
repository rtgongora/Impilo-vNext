package zw.gov.mohcc.impilo.indawo.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SiteResponse(
        UUID siteId,
        UUID tenantId,
        String name,
        String type,
        Object address,
        Object geo,
        String status,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
