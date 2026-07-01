package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read-model DTOs for persisted facility import rows (row-level staging). */
public final class FacilityImportRowDtos {

    private FacilityImportRowDtos() {}

    public record FacilityImportRowView(
            Long id,
            Long importRunId,
            String sourceLabel,
            String sourceRow,
            String correlationKey,
            String province,
            String district,
            String facilityName,
            String facilityCode,
            Double latitude,
            Double longitude,
            String facilityType,
            String ownership,
            String locationCategory,
            String facilityLevel,
            String contact,
            String operatingStatus,
            Integer bedCapacity,
            Map<String, Object> rawValues,
            String outcome,
            String importDecision,
            String exclusionReason,
            String duplicateType,
            String duplicateGroupKey,
            Map<String, Object> acceptableMissing,
            boolean hasAcceptableMissing,
            List<String> validationWarnings,
            String validationErrors,
            Long matchedFacilityId,
            Long resultFacilityId,
            Instant createdAt) {}

    public record PagedRows(
            List<FacilityImportRowView> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}

    /** Review buckets with counts + a bounded preview of rows per bucket. */
    public record ReviewBuckets(
            Long runId,
            Bucket importedRows,
            Bucket missingFacilityCode,
            Bucket duplicateFacilityCodes,
            Bucket duplicateFacilityNames,
            Bucket acceptableMissingFields,
            Bucket failedRows) {}

    public record Bucket(long count, List<FacilityImportRowView> preview) {}

    public record DuplicateGroup(
            String duplicateGroupKey,
            String duplicateType,
            int size,
            List<FacilityImportRowView> rows) {}

    public record DuplicateGroupsResponse(
            Long runId,
            String duplicateType,
            int groupCount,
            List<DuplicateGroup> groups) {}
}
