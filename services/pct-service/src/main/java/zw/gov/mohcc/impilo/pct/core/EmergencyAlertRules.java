package zw.gov.mohcc.impilo.pct.core;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import zw.gov.mohcc.impilo.pct.domain.EmergencyAlertType;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;

/**
 * Decides which alerts an open emergency episode currently warrants.
 *
 * <p>A pure function of (episode, now, thresholds) — no Spring, no repository, no clock of its own.
 * The safety-critical part of this wave is the derivation, and keeping it pure means it is exercised
 * exhaustively by a plain unit test rather than only behind a scheduled job that runs once a minute
 * in an environment nobody is watching.
 *
 * <p><b>Absence is never treated as satisfaction.</b> A null clock does not mean "fine" — it means
 * the thing was never recorded, and for the two conditions where that distinction is a patient-safety
 * matter it is handled explicitly:
 * <ul>
 *   <li>{@code location_confirmed_at} null on an episode that has been open past the grace period is
 *       itself a LOCATION_UNKNOWN — nobody has <em>ever</em> confirmed where this patient is, which
 *       is worse than a stale confirmation, not better.</li>
 *   <li>{@code target_first_contact_at} null means triage never set a target, so no TRIAGE_OVERDUE
 *       can be derived; that gap belongs to triage and is not silently reported as "on time".</li>
 * </ul>
 */
public final class EmergencyAlertRules {

    /** Tunable windows. Defaults are ENGINEERING_SEED pending MoHCC ratification of time targets. */
    public record Thresholds(
            Duration locationStaleAfter,
            Duration locationGraceFromArrival,
            Duration observationStaleAfter,
            Duration acceptanceOverdueAfter) {

        public static Thresholds defaults() {
            return new Thresholds(
                    Duration.ofMinutes(60),   // location confirmation goes stale
                    Duration.ofMinutes(30),   // grace before a never-confirmed location alerts
                    Duration.ofMinutes(60),   // observation gap
                    Duration.ofMinutes(30));  // awaiting acceptance
        }
    }

    /** One alert the sweep should raise, with the evidence that produced it. */
    public record Candidate(
            EmergencyAlertType type,
            String reason,
            OffsetDateTime detectedValueAt) {
    }

    /** Episode states in which alerts are meaningful. Closed and merged episodes never alert. */
    private static final List<String> OPEN_STATES =
            List.of("OPEN_UNTRIAGED", "OPEN_IN_CARE", "OPEN_AWAITING_ACCEPTANCE");

    private EmergencyAlertRules() {
    }

    public static boolean isOpen(EmergencyEpisodeEntity e) {
        return e.getState() != null && OPEN_STATES.contains(e.getState());
    }

    /**
     * @param episode    an emergency episode
     * @param now        evaluation time, injected so tests need no real clock
     * @param t          thresholds
     * @param divergence true when the reconciliation pass disagreed with a peer for this episode
     */
    public static List<Candidate> evaluate(EmergencyEpisodeEntity episode,
                                           OffsetDateTime now,
                                           Thresholds t,
                                           boolean divergence) {
        List<Candidate> out = new ArrayList<>();
        if (!isOpen(episode)) {
            return out;
        }

        // 1. TRIAGE_OVERDUE — a target exists and has passed with no first contact recorded.
        OffsetDateTime target = episode.getTargetFirstContactAt();
        if (target != null && episode.getFirstContactAt() == null && now.isAfter(target)) {
            out.add(new Candidate(EmergencyAlertType.TRIAGE_OVERDUE,
                    "No clinician contact by the triage target (" + target + ")", target));
        }

        // 2. REASSESSMENT_DUE — the re-triage interval has elapsed.
        OffsetDateTime due = episode.getReassessmentDueAt();
        if (due != null && now.isAfter(due)) {
            out.add(new Candidate(EmergencyAlertType.REASSESSMENT_DUE,
                    "Reassessment was due at " + due, due));
        }

        // 3. OBSERVATION_OVERDUE — measured from the last observation, or from arrival when none was
        //    ever recorded. "No observation at all" must alert; it is the emptiest chart, not a quiet one.
        OffsetDateTime lastObs = episode.getLastObservationAt();
        OffsetDateTime obsFrom = lastObs != null ? lastObs : episode.getArrivedAt();
        if (obsFrom != null && now.isAfter(obsFrom.plus(t.observationStaleAfter()))) {
            out.add(new Candidate(EmergencyAlertType.OBSERVATION_OVERDUE,
                    lastObs != null
                            ? "No observation since " + lastObs
                            : "No observation has ever been recorded (arrived " + episode.getArrivedAt() + ")",
                    obsFrom));
        }

        // 4. LOCATION_UNKNOWN — staleness, not nullness of the location string. A never-confirmed
        //    location alerts once past the grace period: nobody has ever known where this patient is.
        OffsetDateTime confirmed = episode.getLocationConfirmedAt();
        if (confirmed != null) {
            if (now.isAfter(confirmed.plus(t.locationStaleAfter()))) {
                out.add(new Candidate(EmergencyAlertType.LOCATION_UNKNOWN,
                        "Location last confirmed at " + confirmed + " ("
                                + Duration.between(confirmed, now).toMinutes() + " min ago)", confirmed));
            }
        } else if (episode.getArrivedAt() != null
                && now.isAfter(episode.getArrivedAt().plus(t.locationGraceFromArrival()))) {
            out.add(new Candidate(EmergencyAlertType.LOCATION_UNKNOWN,
                    "Location has never been confirmed since arrival at " + episode.getArrivedAt(),
                    episode.getArrivedAt()));
        }

        // 5. ACCEPTANCE_OVERDUE — awaiting acceptance too long. Escalates; never closes the episode.
        if ("OPEN_AWAITING_ACCEPTANCE".equals(episode.getState())) {
            // The dedicated clock only — never updated_at, which any unrelated write refreshes and
            // would therefore delay this alarm. If it was never stamped, fall back to arrival: an
            // unstamped awaiting state is a bug, and erring toward alerting is the safe direction.
            OffsetDateTime since = episode.getAwaitingAcceptanceSince() != null
                    ? episode.getAwaitingAcceptanceSince()
                    : episode.getArrivedAt();
            if (since != null && now.isAfter(since.plus(t.acceptanceOverdueAfter()))) {
                out.add(new Candidate(EmergencyAlertType.ACCEPTANCE_OVERDUE,
                        "Awaiting acceptance since " + since
                                + " — responsibility has NOT transferred and no timeout discharges it", since));
            }
        }

        // 6. CORRELATION_DIVERGENCE — supplied by the reconciliation pass, which compares this
        //    episode against its peers rather than trusting that an event arrived.
        if (divergence) {
            out.add(new Candidate(EmergencyAlertType.CORRELATION_DIVERGENCE,
                    "Reconciliation disagreed with peer state for this episode", now));
        }

        return out;
    }
}
