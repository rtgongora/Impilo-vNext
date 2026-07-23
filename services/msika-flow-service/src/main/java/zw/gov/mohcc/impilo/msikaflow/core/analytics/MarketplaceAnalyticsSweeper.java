package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OF-B29 (Wave OF-D) — the scheduled anomaly + fairness sweep (Vol II
 * §13.7/§21). Same shape as {@code MarketplaceSweeper}: a thin timer over
 * unit-testable monitor services. Sweeps are idempotent (finding dedup) so
 * cadence is an ops tunable, not a correctness parameter.
 */
@Component
public class MarketplaceAnalyticsSweeper {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceAnalyticsSweeper.class);

    private final AnomalyMonitors anomalyMonitors;
    private final FairnessMonitors fairnessMonitors;

    public MarketplaceAnalyticsSweeper(AnomalyMonitors anomalyMonitors,
                                       FairnessMonitors fairnessMonitors) {
        this.anomalyMonitors = anomalyMonitors;
        this.fairnessMonitors = fairnessMonitors;
    }

    @Scheduled(fixedDelayString = "${msika-flow.analytics.sweep-interval-ms:300000}")
    public void sweep() {
        runOnce(OffsetDateTime.now());
    }

    /** Shared by the timer and the ops manual-trigger endpoint. */
    public Map<String, Integer> runOnce(OffsetDateTime now) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            counts.putAll(anomalyMonitors.sweep(now));
        } catch (Exception e) {
            log.error("Anomaly sweep failed: {}", e.getMessage(), e);
        }
        try {
            counts.putAll(fairnessMonitors.sweep(now));
        } catch (Exception e) {
            log.error("Fairness sweep failed: {}", e.getMessage(), e);
        }
        int newFindings = counts.entrySet().stream()
                .filter(entry -> !"rankingSnapshotsCaptured".equals(entry.getKey()))
                .mapToInt(Map.Entry::getValue).sum();
        if (newFindings > 0) {
            log.info("Marketplace analytics sweep recorded {} new findings: {}", newFindings, counts);
        }
        return counts;
    }
}
