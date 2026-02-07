package zw.gov.mohcc.impilo.tuso.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FacilitySummaryResponse(
        Long facilityId,
        String facilityName,
        String facilityType,
        String status,
        OccupancySnapshot occupancy,
        List<RecentTelemetry> recentTelemetry,
        int activeAlertCount,
        int workspaceCount,
        int shiftCount
) {
    public record OccupancySnapshot(
            int totalBeds,
            int occupiedBeds,
            int availableBeds,
            Instant snapshotTime
    ) {}

    public record RecentTelemetry(
            String source,
            String metricType,
            BigDecimal metricValue,
            String unit,
            Instant recordedAt
    ) {}
}
