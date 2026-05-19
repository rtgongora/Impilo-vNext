package zw.gov.mohcc.impilo.tshepo.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.config.LegacyRouteUsageTracker;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/legacy")
public class LegacyRouteUsageController {

    private final LegacyRouteUsageTracker tracker;

    public LegacyRouteUsageController(LegacyRouteUsageTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping("/route-usage")
    public ResponseEntity<Map<String, Object>> routeUsage() {
        return ResponseEntity.ok(tracker.snapshot());
    }
}
