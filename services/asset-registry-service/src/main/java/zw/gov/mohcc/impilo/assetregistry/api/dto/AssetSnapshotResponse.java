package zw.gov.mohcc.impilo.assetregistry.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record AssetSnapshotResponse(
        List<AssetResponse> items,
        String cursor,
        int limit,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("as_of") OffsetDateTime asOf
) {}
