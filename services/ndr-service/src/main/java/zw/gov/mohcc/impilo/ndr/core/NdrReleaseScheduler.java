package zw.gov.mohcc.impilo.ndr.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Opt-in scheduled builder for the NDR released zone. Gated OFF by default
 * ({@code ndr.release.scheduler.enabled=false}) so tests and preview never auto-run
 * a de-identification build; the bean is not even instantiated unless explicitly
 * enabled. When on, it rebuilds each configured dataset on the configured cron.
 *
 * <p>Config (all under {@code ndr.release.scheduler}):</p>
 * <ul>
 *   <li>{@code enabled} — must be {@code true} to activate.</li>
 *   <li>{@code cron} — build schedule (default 03:00 daily).</li>
 *   <li>{@code tenant-id} — tenant UUID the scheduled build runs for.</li>
 *   <li>{@code datasets} — comma-separated de-id dataset codes to build.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ndr.release.scheduler.enabled", havingValue = "true")
public class NdrReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(NdrReleaseScheduler.class);

    private final NdrReleaseService releaseService;
    private final String tenantId;
    private final List<String> datasets;

    public NdrReleaseScheduler(NdrReleaseService releaseService,
                               @Value("${ndr.release.scheduler.tenant-id:}") String tenantId,
                               @Value("${ndr.release.scheduler.datasets:}") String datasets) {
        this.releaseService = releaseService;
        this.tenantId = tenantId;
        this.datasets = Arrays.stream(datasets.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Scheduled(cron = "${ndr.release.scheduler.cron:0 0 3 * * *}")
    public void buildConfiguredReleases() {
        if (tenantId == null || tenantId.isBlank() || datasets.isEmpty()) {
            log.warn("NDR release scheduler enabled but no tenant-id / datasets configured — skipping");
            return;
        }
        UUID tenant;
        try {
            tenant = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            log.error("NDR release scheduler tenant-id '{}' is not a UUID — skipping", tenantId);
            return;
        }
        for (String datasetRef : datasets) {
            String correlationId = UUID.randomUUID().toString();
            try {
                var run = releaseService.release(tenant, datasetRef, "national-spine", correlationId);
                log.info("Scheduled NDR release build [dataset={}, status={}, rowsOut={}]",
                        datasetRef, run.getStatus(), run.getRowsOut());
            } catch (Exception e) {
                log.error("Scheduled NDR release build failed [dataset={}]: {}", datasetRef, e.getMessage(), e);
            }
        }
    }
}
