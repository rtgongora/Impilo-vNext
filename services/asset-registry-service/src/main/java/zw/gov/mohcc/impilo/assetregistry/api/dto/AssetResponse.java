package zw.gov.mohcc.impilo.assetregistry.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetResponse(
        UUID assetId,
        UUID tenantId,
        String facilityRef,
        String type,
        String serialNo,
        String status,
        Object metadata,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
