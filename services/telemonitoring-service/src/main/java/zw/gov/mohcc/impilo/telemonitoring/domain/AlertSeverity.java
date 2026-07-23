package zw.gov.mohcc.impilo.telemonitoring.domain;

/**
 * Alert severity (OF-B26, Vol II §14.6). The spec's full six-level ladder is
 * informational → routine → amber → urgent → critical → emergency; this engine
 * implements the three actionable bands the threshold-profile vocabulary
 * ({@code amberMax/redMax/...}) can express today. The lower informational/routine
 * levels have no band vocabulary yet and are honest deferrals (OF-B27 surfaces).
 *
 * <p>§14.6 low-trust ceiling: consumer-grade / DEGRADED readings may raise at most
 * {@link #AMBER} on their own — enforced in the evaluation service, with the applied
 * ceiling stamped on the episode.</p>
 */
public enum AlertSeverity {
    AMBER,
    RED,
    EMERGENCY;

    /** Next severity up the ladder ({@code EMERGENCY} saturates). */
    public AlertSeverity escalated() {
        return switch (this) {
            case AMBER -> RED;
            case RED, EMERGENCY -> EMERGENCY;
        };
    }

    public boolean isAtLeast(AlertSeverity other) {
        return ordinal() >= other.ordinal();
    }
}
