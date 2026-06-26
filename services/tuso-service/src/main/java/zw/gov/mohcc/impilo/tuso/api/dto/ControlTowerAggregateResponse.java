package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.List;

/**
 * Tenant-scoped cross-facility control-tower aggregate. Under aggregate-only
 * visibility, only counts are returned (no per-facility rows).
 */
public record ControlTowerAggregateResponse(
        int facilityCount,
        int openAlertCount,
        String visibility, // ROW_DETAIL | AGGREGATE_ONLY
        List<FacilityRow> facilities // empty under AGGREGATE_ONLY
) {
    public record FacilityRow(
            Long facilityId,
            String name,
            int openAlerts,
            Integer bedOccupancyPercent
    ) {}
}
