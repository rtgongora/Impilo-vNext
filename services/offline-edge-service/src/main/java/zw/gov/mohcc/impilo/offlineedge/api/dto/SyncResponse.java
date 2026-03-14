package zw.gov.mohcc.impilo.offlineedge.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /internal/v1/offline/sync}.
 */
public record SyncResponse(
        UUID batchId,
        String status,
        int totalActions,
        int acceptedCount,
        int rejectedCount,
        List<SyncActionResult> results
) {
    public record SyncActionResult(
            UUID entryId,
            UUID actionId,
            String status,
            String error
    ) {}
}
