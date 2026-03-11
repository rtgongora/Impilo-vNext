package zw.gov.mohcc.impilo.assetregistry.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AssetSnapshotResponse(
        List<AssetResponse> items,
        String cursor,
        int limit,
        boolean hasMore,
        OffsetDateTime asOf
) {}
