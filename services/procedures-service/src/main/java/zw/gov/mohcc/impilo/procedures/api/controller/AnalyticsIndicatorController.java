package zw.gov.mohcc.impilo.procedures.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.AnalyticsDtos.IndicatorSummary;
import zw.gov.mohcc.impilo.procedures.api.dto.AnalyticsDtos.IndicatorView;
import zw.gov.mohcc.impilo.procedures.core.AnalyticsIndicatorService;

import java.util.UUID;

/**
 * Read API for the pipeline analytics indicator catalogue (§26). Read-only — engine-not-store;
 * see {@code AnalyticsIndicatorService}'s javadoc for why this never computes a number itself.
 *
 * <p>Indicator codes travel as a QUERY PARAMETER, not a REST path variable — the same route-shape
 * choice every controller in this programme has made from the start (P-R.4's finding).</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures")
public class AnalyticsIndicatorController {

    private final AnalyticsIndicatorService service;

    public AnalyticsIndicatorController(AnalyticsIndicatorService service) {
        this.service = service;
    }

    @GetMapping("/analytics/indicators")
    public IndicatorSummary indicatorCatalogue(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return service.indicatorCatalogue(tenantId);
    }

    @GetMapping("/analytics/indicator")
    public IndicatorView indicator(@RequestHeader("X-Tenant-ID") UUID tenantId, @RequestParam String code) {
        return service.indicator(tenantId, code);
    }
}
