package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import zw.gov.mohcc.impilo.pct.domain.EmergencyAlertType;
import zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;

/**
 * The reconciliation pass {@link EmergencyAlertRules#evaluate} and {@link EmergencyAlertSweepJob}
 * both cite but neither performs: "the reconciler that computes divergence against peers is wired
 * separately and calls raiseAlert directly." This is that reconciler.
 *
 * <p><b>Why this is a separate job on its own (slower) schedule, not folded into the alert sweep.</b>
 * Every other alert condition reads a local DERIVED PROJECTION column so the sweep keeps firing when
 * a peer is degraded — that is the whole point of R5's declared-projection design. A reconciliation
 * pass is the deliberate exception: it EXISTS to call a peer and notice disagreement, so it is the one
 * place in this pack a live cross-service read belongs. Putting it in the fires-every-60s sweep would
 * mean every episode's alert evaluation blocks on a network round trip; keeping it separate and slower
 * (5 minutes) means a degraded DAIDZAI slows only reconciliation, never the other five alerts.
 *
 * <p>Scope: only episodes carrying a non-null {@code traumaEpisodeId} are reconciled — those are the
 * ones DAIDZAI is supposed to already agree on. An episode with no trauma correlation at all is not a
 * divergence; that is the ordinary walk-in case, and "never linked" is already the concern of
 * DAIDZAI's own {@code /unanchored} sweep (W3b), not this one. This job asks a narrower question: for
 * episodes that ARE supposed to be linked, does the peer still agree?
 *
 * <p>Absence of an answer (DAIDZAI unreachable) is never treated as agreement OR as divergence — it is
 * treated as "cannot conclude this round", exactly as {@link EmergencyAlertRules}'s own class comment
 * insists absence is never satisfaction.
 */
@Component
public class EmergencyReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(EmergencyReconciliationJob.class);

    private static final List<String> OPEN_STATES =
            List.of("OPEN_UNTRIAGED", "OPEN_IN_CARE", "OPEN_AWAITING_ACCEPTANCE");

    private final EmergencyEpisodeRepository episodeRepository;
    private final DaidzaiEpisodeClient daidzaiClient;
    private final EmergencyAlertSweepJob alertSweepJob;

    public EmergencyReconciliationJob(EmergencyEpisodeRepository episodeRepository,
                                      DaidzaiEpisodeClient daidzaiClient,
                                      EmergencyAlertSweepJob alertSweepJob) {
        this.episodeRepository = episodeRepository;
        this.daidzaiClient = daidzaiClient;
        this.alertSweepJob = alertSweepJob;
    }

    @Scheduled(fixedDelayString = "${pct.emergency.reconciliation.sweep-interval-ms:300000}",
            initialDelayString = "${pct.emergency.reconciliation.sweep-initial-delay-ms:60000}")
    public void sweep() {
        int divergent = reconcileAll(OffsetDateTime.now());
        if (divergent > 0) {
            log.info("Emergency reconciliation sweep raised CORRELATION_DIVERGENCE for {} episode(s)", divergent);
        }
    }

    /** Reconcile every open, trauma-correlated episode against DAIDZAI. Returns the number diverged. */
    public int reconcileAll(OffsetDateTime now) {
        List<EmergencyEpisodeEntity> open = episodeRepository.findByStateIn(OPEN_STATES);
        int diverged = 0;
        for (EmergencyEpisodeEntity episode : open) {
            if (episode.getTraumaEpisodeId() == null) {
                continue; // not correlated at all — DAIDZAI's own unanchored sweep owns that gap
            }
            try {
                if (reconcileOne(episode, now)) {
                    diverged++;
                }
            } catch (RuntimeException ex) {
                // Isolated on purpose, same discipline as the alert and expiry sweeps: one bad
                // episode must not abort reconciliation for every other correlated episode.
                log.error("Reconciliation failed for episode {}: {}", episode.getEpisodeId(), ex.toString());
            }
        }
        return diverged;
    }

    /**
     * One episode. The peer read happens OUTSIDE any transaction (it is a network call); only the
     * resulting alert write, if any, is transactional.
     */
    private boolean reconcileOne(EmergencyEpisodeEntity episode, OffsetDateTime now) {
        Map<String, Object> view = daidzaiClient.getTraumaEpisode(episode.getTenantId(), episode.getTraumaEpisodeId());
        if (view == null) {
            return false; // DAIDZAI unreachable or the episode is gone — cannot conclude, not a divergence
        }

        String reason = divergenceReason(episode, view);
        if (reason == null) {
            return false;
        }

        return raiseDivergence(episode, reason, now);
    }

    /** @return a human-readable divergence reason, or {@code null} if the peer agrees. */
    private static String divergenceReason(EmergencyEpisodeEntity episode, Map<String, Object> view) {
        Object mergedInto = view.get("mergedIntoId");
        if (mergedInto != null && !mergedInto.toString().isBlank()) {
            return "DAIDZAI trauma episode " + episode.getTraumaEpisodeId()
                    + " has been merged into " + mergedInto + " — this episode's correlation is stale";
        }

        Object peerAnchor = view.get("pctEmergencyEpisodeId");
        if (peerAnchor != null && !peerAnchor.toString().isBlank()
                && !peerAnchor.toString().equals(episode.getEpisodeId().toString())) {
            return "DAIDZAI trauma episode " + episode.getTraumaEpisodeId() + " is anchored to a "
                    + "DIFFERENT PCT episode (" + peerAnchor + ") than this one — a correlation split";
        }

        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean raiseDivergence(EmergencyEpisodeEntity episode, String reason, OffsetDateTime now) {
        var candidate = new EmergencyAlertRules.Candidate(EmergencyAlertType.CORRELATION_DIVERGENCE, reason, now);
        return alertSweepJob.raiseAlert(episode, candidate, now);
    }
}
