package zw.gov.mohcc.impilo.msika.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CatalogView(
    String catalogId,
    UUID tenantId,
    String scope,
    String name,
    String description,
    String status,
    String version,
    String parentCatalogId,
    String checksum,
    long itemCount,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
