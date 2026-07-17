package zw.gov.mohcc.impilo.obs.core;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Disclosure-limited public projection of the service-status board.
 *
 * <p>Contains only a generation timestamp, the staleness threshold, and a list
 * of per-service {@link PublicServiceStatusItem}. It carries none of the
 * internal aggregate counts (total/healthy/stale) or raw heartbeat rows exposed
 * by the internal health summary.</p>
 */
public record PublicServiceStatusSummary(
        OffsetDateTime generatedAt,
        int staleThresholdMinutes,
        List<PublicServiceStatusItem> services) {
}
