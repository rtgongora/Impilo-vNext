package zw.gov.mohcc.impilo.abis.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.abis.core.AbisStatsService;

import java.util.Map;
import java.util.UUID;

/**
 * ABIS operational dashboards + governed threshold config (W3e).
 *
 * <ul>
 *   <li>{@code GET /v1/abis/stats} — template volumes, quality distribution, adjudication queue.</li>
 *   <li>{@code GET /v1/abis/config/thresholds} — the governed dedup/quality thresholds in force.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/abis")
public class AbisStatsController {

    private final AbisStatsService statsService;

    public AbisStatsController(AbisStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(statsService.stats(tenantId));
    }

    @GetMapping("/config/thresholds")
    public ResponseEntity<Map<String, Object>> thresholds() {
        return ResponseEntity.ok(statsService.thresholdView());
    }
}
