package zw.gov.mohcc.impilo.pct.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical lifecycle states of a telemedicine referral (the teleconsult case).
 *
 * <p>The nine states in the first block are the ones already exercised on the live
 * {@code pct_referrals} spine; the remainder are additive target states from the
 * National Telemedicine &amp; Virtual Care Specification §11.3 (guarded by
 * {@link zw.gov.mohcc.impilo.pct.core.ReferralStateMachine}). The column is a
 * {@code VARCHAR}; this enum names the values without constraining the column
 * (unknown/legacy strings resolve leniently — see {@link #fromLenient(String)}).</p>
 */
public enum ReferralStatus {

    // --- currently live on pct_referrals ---
    DRAFT,
    ROUTED,
    SUBMITTED,
    ACCEPTED,
    IN_REVIEW,
    SCHEDULED,
    RESPONDED,
    DECLINED,
    COMPLETED,

    // --- additive target states (§11.3) ---
    OFFERED,
    NEEDS_MORE_INFORMATION,
    AWAITING_LOCAL_ACTION,
    AWAITING_PATIENT_ACTION,
    AWAITING_RESULTS,
    FOLLOW_UP_DUE,
    COMPLETED_AWAITING_CLOSURE,
    CLOSED,
    REOPENED,
    ESCALATED,
    TRANSFERRED,
    CANCELLED,
    EXPIRED,
    ABANDONED,
    ENTERED_IN_ERROR;

    /**
     * Resolve a stored status string to a {@link ReferralStatus} without throwing.
     * Returns empty for null/blank or any legacy/unknown value so the guard can
     * treat it leniently rather than 500 on historical data.
     */
    public static Optional<ReferralStatus> fromLenient(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ReferralStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
