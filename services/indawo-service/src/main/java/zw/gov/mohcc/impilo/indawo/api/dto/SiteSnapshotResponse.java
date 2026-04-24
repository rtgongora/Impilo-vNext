package zw.gov.mohcc.impilo.indawo.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record SiteSnapshotResponse(
        List<SiteResponse> items,
        String cursor,
        int limit,
        boolean hasMore,
        @JsonProperty("as_of") OffsetDateTime asOf
) {}
