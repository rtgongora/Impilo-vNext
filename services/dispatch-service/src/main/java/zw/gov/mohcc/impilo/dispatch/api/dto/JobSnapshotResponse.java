package zw.gov.mohcc.impilo.dispatch.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record JobSnapshotResponse(
        List<JobResponse> items,
        String cursor,
        int limit,
        boolean hasMore,
        OffsetDateTime asOf) {
}
