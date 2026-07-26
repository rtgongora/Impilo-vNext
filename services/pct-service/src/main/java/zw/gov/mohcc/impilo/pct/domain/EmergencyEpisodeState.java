package zw.gov.mohcc.impilo.pct.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * The emergency episode lifecycle.
 *
 * <p>Two properties of this graph are the reason the pack exists, and both are structural rather
 * than conventional:
 *
 * <ol>
 *   <li><b>{@link #OPEN_IN_CARE} cannot reach {@link #CLOSED_HANDED_OVER} directly.</b> It must pass
 *       through {@link #OPEN_AWAITING_ACCEPTANCE}. Raising a referral, an admission request or a
 *       theatre request moves the episode into the waiting state and nothing more — the episode
 *       closes only when an accepting party writes back with its own record id. A request is not a
 *       handover.</li>
 *   <li><b>{@link #OPEN_AWAITING_ACCEPTANCE} can return to {@link #OPEN_IN_CARE}.</b> A declined or
 *       expired handover gives the patient back to emergency care. There is deliberately no timeout
 *       that discharges responsibility: a patient nobody has accepted is still emergency's
 *       patient.</li>
 * </ol>
 */
public enum EmergencyEpisodeState {

    /** Pre-alerted, not yet arrived. The only state permitted to have no PCT anchor. */
    PRE_ARRIVAL,

    /** Arrived, not yet triaged. */
    OPEN_UNTRIAGED,

    /** Under emergency care. */
    OPEN_IN_CARE,

    /** A handover has been requested and not yet accepted. Still emergency's patient. */
    OPEN_AWAITING_ACCEPTANCE,

    /** Closed by an accepted handover, carrying the accepting party's own record id. */
    CLOSED_HANDED_OVER,

    CLOSED_DISCHARGED,
    CLOSED_DIED,
    CLOSED_LEFT_BEFORE_ASSESSMENT,
    CLOSED_LEFT_AGAINST_ADVICE,
    CLOSED_ABSCONDED,
    CLOSED_DECLINED_CARE,

    /** Absorbed into another episode. Never deleted, so correlations still resolve. */
    MERGED;

    private static final Set<EmergencyEpisodeState> TERMINAL = EnumSet.of(
            CLOSED_HANDED_OVER, CLOSED_DISCHARGED, CLOSED_DIED, CLOSED_LEFT_BEFORE_ASSESSMENT,
            CLOSED_LEFT_AGAINST_ADVICE, CLOSED_ABSCONDED, CLOSED_DECLINED_CARE, MERGED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** True while the patient is still emergency's responsibility. */
    public boolean isOpen() {
        return !isTerminal();
    }

    public Set<EmergencyEpisodeState> allowedNext() {
        return switch (this) {
            // A pre-alert either arrives, is stood down, or turns out to be a duplicate of an
            // episode already open. It cannot jump to in-care without an arrival.
            case PRE_ARRIVAL -> EnumSet.of(OPEN_UNTRIAGED, CLOSED_DECLINED_CARE, MERGED);

            // A patient can die or leave before anyone assesses them; both are real and both must
            // be recordable without pretending care happened first.
            case OPEN_UNTRIAGED -> EnumSet.of(
                    OPEN_IN_CARE, CLOSED_LEFT_BEFORE_ASSESSMENT, CLOSED_DIED, MERGED);

            // Note the absence of CLOSED_HANDED_OVER. Going through OPEN_AWAITING_ACCEPTANCE is the
            // whole mechanism: it is what makes an unaccepted patient visible instead of closed.
            case OPEN_IN_CARE -> EnumSet.of(
                    OPEN_AWAITING_ACCEPTANCE, CLOSED_DISCHARGED, CLOSED_DIED,
                    CLOSED_LEFT_AGAINST_ADVICE, CLOSED_ABSCONDED, CLOSED_DECLINED_CARE, MERGED);

            // OPEN_IN_CARE is reachable again on purpose: a declined or expired handover returns
            // the patient rather than stranding them between services.
            case OPEN_AWAITING_ACCEPTANCE -> EnumSet.of(
                    CLOSED_HANDED_OVER, OPEN_IN_CARE, CLOSED_DIED, MERGED);

            default -> EnumSet.noneOf(EmergencyEpisodeState.class);
        };
    }

    public boolean canTransitionTo(EmergencyEpisodeState next) {
        return allowedNext().contains(next);
    }
}
