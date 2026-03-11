package zw.gov.mohcc.impilo.indawo.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SiteSnapshotResponse(
        List<SiteResponse> items,
        String cursor,
        int limit,
        boolean hasMore,
        OffsetDateTime asOf
) {}
