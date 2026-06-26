package zw.gov.mohcc.impilo.varapi.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of a {@link BulkPreloadRequest}. Reports per-row outcome so the
 * submitter sees exactly which skeletons were created, which were skipped as
 * duplicates, and which failed validation — no silent drops.
 */
public record BulkPreloadResponse(
        UUID batchId,
        int created,
        int skipped,
        int failed,
        List<PreloadResult> results
) {
    public record PreloadResult(
            int rowIndex,
            String outcome,            // CREATED | SKIPPED_DUPLICATE | FAILED
            String providerPublicId,   // present when CREATED
            /** Raw claim token — returned ONCE here only, never persisted. */
            String claimToken,
            String message
    ) {}
}
