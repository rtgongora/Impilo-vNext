package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyHandoverRepository;

/**
 * The scheduled job V204's own migration comment deferred: "expiry is an explicit action (by a
 * clinician or a later scheduled job), never a silent state change." A PENDING handover whose
 * {@code response_due_at} (V209) has passed is expired here — returning the episode to
 * OPEN_IN_CARE, exactly as a clinician-initiated expiry does via
 * {@link EmergencyEpisodeService#expireHandover}, which this job calls directly rather than
 * duplicating its logic.
 *
 * <p>System-initiated, so it writes an explicit system actor rather than borrowing a user's
 * identity — the same convention as {@link EmergencyAlertSweepJob}.
 *
 * <p><b>Per-handover isolation is deliberate</b>, for the same reason as the alert sweep: one bad
 * row must not abort expiry for every other unanswered handover in the estate.
 */
@Component
public class HandoverExpirySweepJob {

    private static final Logger log = LoggerFactory.getLogger(HandoverExpirySweepJob.class);

    /** Distinct from the alert sweep's system actor — the audit trail should say which sweep acted. */
    private static final String SYSTEM_ACTOR = "system:emergency-handover-expiry-sweep";

    private final EmergencyHandoverRepository handoverRepository;
    private final EmergencyEpisodeService episodeService;

    public HandoverExpirySweepJob(EmergencyHandoverRepository handoverRepository,
                                  EmergencyEpisodeService episodeService) {
        this.handoverRepository = handoverRepository;
        this.episodeService = episodeService;
    }

    @Scheduled(fixedDelayString = "${pct.emergency.handover.sweep-interval-ms:60000}",
            initialDelayString = "${pct.emergency.handover.sweep-initial-delay-ms:50000}")
    public void sweep() {
        int expired = expireDue(OffsetDateTime.now());
        if (expired > 0) {
            log.info("Handover expiry sweep expired {} unanswered request(s)", expired);
        }
    }

    /** Evaluate every PENDING handover past its deadline. Returns the number expired. */
    public int expireDue(OffsetDateTime now) {
        List<EmergencyHandoverEntity> due = handoverRepository.findExpiredPending(now);
        int expired = 0;
        for (EmergencyHandoverEntity h : due) {
            try {
                expireOne(h.getHandoverId(), h.getTenantId());
                expired++;
            } catch (RuntimeException ex) {
                // Isolated on purpose — see the class comment. Logged loudly, never rethrown.
                log.error("Handover expiry sweep failed for handover {}: {}", h.getHandoverId(), ex.toString());
            }
        }
        return expired;
    }

    /**
     * One handover, one transaction. {@code REQUIRES_NEW} so a failure here rolls back only this
     * handover, not the whole sweep — same isolation contract as
     * {@link EmergencyAlertSweepJob#sweepOneEpisode}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(java.util.UUID handoverId, java.util.UUID tenantId) {
        episodeService.expireHandover(handoverId, tenantId, SYSTEM_ACTOR, null);
    }
}
