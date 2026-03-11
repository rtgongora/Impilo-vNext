package zw.gov.mohcc.impilo.assetregistry.api.dto;

import java.util.UUID;

public record ExternalAssetResponse(
        UUID assetId,
        String facilityRef,
        String type,
        String status
) {}
