package zw.gov.mohcc.impilo.experience.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-replica claim for patient-facing appointment sends.
 *
 * <p>{@code experience-bff} is the only multi-replica Java service in the estate, so its schedulers
 * are the only ones that can fire twice for one due item. A Redis {@code SET key value NX EX} is the
 * primitive that makes exactly one replica the owner of each send: it is a single atomic round trip
 * against the one Redis both replicas share, so the losing replica learns it lost before it sends.
 *
 * <p><b>Failure posture — deliberate.</b> When Redis is unreachable there is no shared place to make
 * a claim, so no replica can know whether a peer is already sending. This class therefore
 * <b>refuses to claim</b> ({@link ClaimOutcome#UNAVAILABLE}) rather than degrade to a per-pod
 * in-memory set. A per-pod set is not a claim at all — every replica would win it independently and
 * every replica would send, which for this workload means duplicate SMS to real patients.
 *
 * <p>The product call behind that: for an appointment reminder, a <b>late reminder is recoverable
 * and a duplicate one is not</b>. Refusing is only safe because both callers retry rather than drop:
 * <ul>
 *   <li>{@code AppointmentCommsWorkflowService#dispatchUpcomingReminders} re-scans a window two
 *       hours wide (24h ± 1h) on an hourly cron, so each due appointment is offered at least twice;
 *       a refusal during a short outage is picked up by the next run.</li>
 *   <li>{@code BookingOutboxCommsPoller} holds its cursor at a refused event instead of advancing
 *       past it, so the event is re-offered on the next poll.</li>
 * </ul>
 * If either of those retry properties is ever removed, this refusal turns into a silent drop and the
 * posture must be revisited with it.
 */
@Component
public class AppointmentReminderReceiptStore {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderReceiptStore.class);
    private static final String KEY_PREFIX = "impilo:appointment:reminder:";
    private static final Duration TTL = Duration.ofDays(14);

    private final StringRedisTemplate redis;

    /** Tracks the healthy↔degraded edge so an outage is logged once, not once per due appointment. */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public AppointmentReminderReceiptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** The result of asking for exclusive ownership of one send. */
    public enum ClaimOutcome {
        /** This replica won; it owns the send. */
        CLAIMED,
        /** A peer replica (or an earlier run) already owns it; this replica must not send. */
        ALREADY_CLAIMED,
        /** The dedup key was unusable, so nothing could be deduplicated and nothing was sent. */
        INVALID_KEY,
        /** No shared claim could be made at all — refuse now, retry when the store returns. */
        UNAVAILABLE;

        /** True only when this replica owns the send. */
        public boolean claimed() {
            return this == CLAIMED;
        }

        /**
         * True when the work was neither done here nor provably done elsewhere — the caller must
         * re-offer this item later and must not treat it as handled.
         */
        public boolean mustRetry() {
            return this == UNAVAILABLE;
        }
    }

    /**
     * Claims exclusive ownership of one send across every replica.
     *
     * @return {@link ClaimOutcome#CLAIMED} only if this replica may send.
     */
    public ClaimOutcome claim(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return ClaimOutcome.INVALID_KEY;
        }
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(KEY_PREFIX + dedupKey, "1", TTL);
            if (claimed == null) {
                // No definite answer (no reply, or a pipelined/queued template). We did not observe
                // ourselves winning, so we must not assume we did.
                noteDegraded("Redis returned no claim result");
                return ClaimOutcome.UNAVAILABLE;
            }
            noteHealthy();
            return claimed ? ClaimOutcome.CLAIMED : ClaimOutcome.ALREADY_CLAIMED;
        } catch (Exception ex) {
            noteDegraded(ex.toString());
            return ClaimOutcome.UNAVAILABLE;
        }
    }

    /**
     * Convenience form for callers that only need to know whether they may send.
     *
     * <p>Note this returns {@code false} both when a peer already claimed the send and when no claim
     * could be made at all. Callers that must distinguish "handled" from "not yet handled" — anything
     * advancing a cursor — must use {@link #claim(String)} and check {@link ClaimOutcome#mustRetry()}.
     */
    public boolean tryClaim(String dedupKey) {
        return claim(dedupKey).claimed();
    }

    private void noteDegraded(String cause) {
        if (degraded.compareAndSet(false, true)) {
            log.error("Appointment reminder dedup UNAVAILABLE ({}). Patient-facing sends are being "
                    + "withheld rather than sent once per replica; they resume when the store returns.", cause);
        } else {
            log.debug("Appointment reminder dedup still unavailable: {}", cause);
        }
    }

    private void noteHealthy() {
        if (degraded.compareAndSet(true, false)) {
            log.warn("Appointment reminder dedup recovered — withheld sends resume on the next run.");
        }
    }
}
