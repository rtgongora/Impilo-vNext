package zw.gov.mohcc.impilo.ubomi.integration.rg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scheduled reconcile loop for the RG adapter (W1c). Re-drives parked
 * {@code RG_PENDING} verifications and expires them to {@code RG_UNAVAILABLE}
 * past the governed TTL. Design law honoured here:
 * <ul>
 *   <li><b>Per-row failure isolation</b> — each row is driven in its own
 *       transaction ({@link RgVerificationService#reDrive}); a single failing
 *       RG call can never head-of-line-block the rest of the batch (the
 *       outbox/EventEnvelope stall class).</li>
 *   <li><b>Bounded jittered backoff</b> — a row is only retried once its
 *       attempt-scaled backoff window (with jitter) has elapsed, so a persistent
 *       RG outage does not hot-loop.</li>
 * </ul>
 *
 * <p>Runs whether or not the RG flag is on: with the flag off it still performs
 * TTL expiry (honest {@code RG_UNAVAILABLE}); it never fabricates a verdict.</p>
 */
@Component
public class RgOutboundRelay {

    private static final Logger log = LoggerFactory.getLogger(RgOutboundRelay.class);

    /** Base backoff between retry attempts of the same row. */
    private static final long BASE_BACKOFF_MINUTES = 15;
    /** Cap so backoff never grows unbounded. */
    private static final long MAX_BACKOFF_MINUTES = 6 * 60;

    private final RgVerificationRepository ledger;
    private final RgVerificationService service;
    private final UbomiRgProperties props;

    public RgOutboundRelay(RgVerificationRepository ledger,
                           RgVerificationService service,
                           UbomiRgProperties props) {
        this.ledger = ledger;
        this.service = service;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${ubomi.rg.reconcile-interval-ms:60000}")
    public void reconcile() {
        List<RgVerificationEntity> pending =
                ledger.findTop100ByStatusOrderByCreatedAtAsc(RgVerificationEntity.RG_PENDING);
        if (pending.isEmpty()) {
            return;
        }
        int driven = 0;
        int terminal = 0;
        for (RgVerificationEntity row : pending) {
            if (!dueForRetry(row)) {
                continue;
            }
            driven++;
            try {
                if (service.reDrive(row.getId(), props.getPendingTtlHours())) {
                    terminal++;
                }
            } catch (Exception e) {
                // Per-row isolation — never let one row stall the batch.
                log.warn("RG reconcile failed for row id={}: {}", row.getId(), e.toString());
            }
        }
        if (driven > 0) {
            log.info("RG reconcile: drove {} pending row(s), {} reached terminal state", driven, terminal);
        }
    }

    /** Attempt-scaled backoff window (with jitter) must have elapsed. */
    private boolean dueForRetry(RgVerificationEntity row) {
        OffsetDateTime last = row.getLastAttemptAt();
        if (last == null) {
            return true; // never attempted
        }
        long backoff = Math.min(BASE_BACKOFF_MINUTES * (1L << Math.min(row.getAttemptCount(), 8)),
                MAX_BACKOFF_MINUTES);
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, backoff / 4 + 1));
        return last.plusMinutes(backoff + jitter).isBefore(OffsetDateTime.now());
    }
}
