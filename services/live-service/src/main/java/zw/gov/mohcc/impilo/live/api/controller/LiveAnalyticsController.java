package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.AnalyticsService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAnalyticsSnapshotEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/analytics")
public class LiveAnalyticsController {

    private final AnalyticsService analyticsService;

    public LiveAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/{eventId}/snapshot")
    public void captureSnapshot(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        analyticsService.captureSnapshot(tenantId, eventId);
    }

    @GetMapping("/{eventId}/dashboard")
    public LiveDtos.AnalyticsDashboard dashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return new LiveDtos.AnalyticsDashboard(eventId,
                analyticsService.getDashboardMetrics(tenantId, eventId));
    }

    @GetMapping("/{eventId}/history")
    public List<LiveEventAnalyticsSnapshotEntity> history(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestParam String metricName) {
        return analyticsService.getHistory(tenantId, eventId, metricName);
    }
}
