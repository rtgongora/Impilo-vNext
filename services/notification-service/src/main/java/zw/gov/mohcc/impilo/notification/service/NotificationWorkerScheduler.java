package zw.gov.mohcc.impilo.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.notification.api.dto.WorkerResponse;

/**
 * Autonomous dispatch loop for pending notifications.
 *
 * <p>Without this scheduler, PENDING notifications only move when an operator
 * manually POSTs {@code /internal/v1/worker/run} — nothing in any deployment
 * path does that, so enqueued notifications (appointment comms, campaign
 * deliveries, approved templates) would sit undispatched forever. The manual
 * endpoint remains available for operator-triggered drains.</p>
 *
 * <p>Disable with {@code notification.worker.scheduler-enabled=false} (e.g. in
 * environments where an external job runner owns the drain cadence).</p>
 */
@Component
@ConditionalOnProperty(name = "notification.worker.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorkerScheduler.class);

    private final WorkerService workerService;
    private final int batchLimit;

    public NotificationWorkerScheduler(
            WorkerService workerService,
            @Value("${notification.worker.batch-limit:25}") int batchLimit) {
        this.workerService = workerService;
        this.batchLimit = batchLimit;
        log.info("Notification worker scheduler enabled (batch-limit={})", batchLimit);
    }

    @Scheduled(fixedDelayString = "${notification.worker.poll-interval-ms:10000}")
    public void dispatchPending() {
        try {
            WorkerResponse result = workerService.processPending(batchLimit);
            if (result.processed() > 0) {
                log.info("Scheduled worker run: processed={} sent={} failed={}",
                        result.processed(), result.sent(), result.failed());
            }
        } catch (Exception e) {
            // Never let one bad run kill the fixedDelay loop; next tick retries.
            log.error("Scheduled notification worker run failed: {}", e.getMessage(), e);
        }
    }
}
