package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.api.dto.AnalyticsDtos.IndicatorSummary;
import zw.gov.mohcc.impilo.surgery.api.dto.AnalyticsDtos.IndicatorView;
import zw.gov.mohcc.impilo.surgery.core.AnalyticsIndicatorService;

import java.util.UUID;

/**
 * Read API for the surgical-pack analytics indicator catalogue (§23). Read-only — engine-not-
 * store; see {@code AnalyticsIndicatorService}'s javadoc.
 *
 * <p>Indicator codes travel as a QUERY PARAMETER, not a REST path variable — the route-shape law
 * every controller in this programme has followed since P-R.4's finding.</p>
 */
@RestController
@RequestMapping("/internal/v1/surgery")
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
