package zw.gov.mohcc.impilo.tuso.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

import java.util.UUID;

/**
 * Scheduled trigger for GOFR synchronization.
 *
 * <p>Only active when {@code tuso.gofr.enabled=true}. Uses the cron expression
 * from {@code tuso.gofr.sync-cron} to determine the sync schedule.</p>
 *
 * <p>For each tenant, calls {@link GofrSyncAdapter#syncFromGofr(UUID)} to pull
 * the latest facility data from the external GOFR instance.</p>
 */
@Component
@ConditionalOnProperty(name = "tuso.gofr.enabled", havingValue = "true")
public class GofrSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GofrSyncScheduler.class);

    private final GofrSyncAdapter gofrSyncAdapter;
    private final TusoProperties tusoProperties;

    public GofrSyncScheduler(GofrSyncAdapter gofrSyncAdapter,
                              TusoProperties tusoProperties) {
        this.gofrSyncAdapter = gofrSyncAdapter;
        this.tusoProperties = tusoProperties;
    }

    /**
     * Scheduled GOFR sync task. Runs on the cron schedule configured in
     * {@code tuso.gofr.sync-cron} (default: every 6 hours).
     *
     * <p>Double-checks that GOFR mode is enabled before executing the sync.
     * For each configured tenant, pulls the latest facility data from GOFR.</p>
     */
    @Scheduled(cron = "${tuso.gofr.sync-cron:0 */6 * * * *}")
    public void scheduledSync() {
        if (!tusoProperties.getGofr().isEnabled()) {
            log.trace("GOFR sync disabled; skipping scheduled run");
            return;
        }

        log.info("Starting scheduled GOFR sync");

        try {
            UUID systemTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            gofrSyncAdapter.syncFromGofr(systemTenantId);
        } catch (Exception e) {
            log.error("Scheduled GOFR sync failed: {}", e.getMessage(), e);
        }
    }
}
