package zw.gov.mohcc.impilo.analyticspipeline.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.analyticspipeline.core.TelemedicineAnalyticsService;

import java.util.Map;

/**
 * Telemedicine lifecycle analytics ingest and SLA aggregate queries.
 */
@RestController
@RequestMapping("/internal/v1/telemedicine")
public class TelemedicineAnalyticsController {

    private final TelemedicineAnalyticsService analyticsService;

    public TelemedicineAnalyticsController(TelemedicineAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/sla")
    public ResponseEntity<Map<String, Object>> slaAggregates(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(analyticsService.slaAggregates(facilityId, from, to));
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingestEvent(@RequestBody Map<String, Object> event) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(analyticsService.ingestEvent(event));
    }
}
