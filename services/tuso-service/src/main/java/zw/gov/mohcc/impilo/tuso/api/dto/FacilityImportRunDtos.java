package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-model DTOs for persisted facility master import runs ({@code tuso.facility_import_run}).
 * Surfaces run-level provenance, totals and the exclusion/completeness breakdown so operators can
 * see the absorption state in the product, not just in backend tests.
 */
public final class FacilityImportRunDtos {

    private FacilityImportRunDtos() {}

    /** One import run/batch. Row-level staging is not persisted per run yet (documented as next slice). */
    public record FacilityImportRunView(
            Long runId,
            String packId,
            String sourceLabel,
            /** Not captured per-run yet (import is submitted as parsed records); null until staging lands. */
            String sourceFileName,
            String status,
            boolean dryRun,
            String initiatedBy,
            Instant startedAt,
            Instant completedAt,
            RunTotals totals,
            /** Raw validation/import summary as persisted (quality_report JSONB). */
            Map<String, Object> qualitySummary
    ) {}

    /** Totals + breakdown, all derived from real row outcomes at import time. */
    public record RunTotals(
            long recordsTotal,
            long recordsImported,
            long recordsCreated,
            long recordsUpdated,
            long recordsSkipped,
            long recordsFailed,
            long warningsCount,
            long missingFacilityCode,
            long duplicateFacilityCode,
            long duplicateFacilityName,
            long excludedTotal,
            long acceptableMissing,
            long withValidCoordinates
    ) {}

    public record FacilityImportRunListResponse(
            int count,
            List<FacilityImportRunView> runs
    ) {}
}
