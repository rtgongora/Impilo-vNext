package zw.gov.mohcc.impilo.pct.domain;

/**
 * The six emergency alert conditions (pct V203).
 *
 * <p>Each reads a clock that {@code emergency_episode} already carries as a declared DERIVED
 * PROJECTION. That is what lets the sweep keep firing when a peer service is degraded — an alert
 * that depended on a cross-service HTTP read would go quiet exactly when it is most needed, which
 * is the failure mode this estate has already lived through with dead cross-service Kafka seams.
 */
public enum EmergencyAlertType {

    /** Triaged, but nobody has made first contact by the target. Reads {@code target_first_contact_at}. */
    TRIAGE_OVERDUE("CRITICAL", 10),

    /** A re-triage interval has elapsed. Reads {@code reassessment_due_at}. */
    REASSESSMENT_DUE("HIGH", 15),

    /** No observation recorded for too long. Reads {@code last_observation_at}. */
    OBSERVATION_OVERDUE("HIGH", 15),

    /**
     * Nobody has confirmed where the patient is. Fires on the STALENESS of
     * {@code location_confirmed_at}, never on a null location — a nullable location field is null
     * forever and alerts on nothing, whereas the patient wheeled to CT and forgotten has a
     * confirmation timestamp that simply stops being refreshed.
     */
    LOCATION_UNKNOWN("CRITICAL", 10),

    /**
     * {@code OPEN_AWAITING_ACCEPTANCE} for too long. No timeout may ever discharge emergency
     * responsibility, so this escalates and opens a rito safety case; it never closes the episode.
     */
    ACCEPTANCE_OVERDUE("CRITICAL", 10),

    /**
     * The reconciliation sweep re-derived an open episode against its peers and disagreed. An
     * event-only design fails silently precisely when the broker fails; this notices the silence.
     */
    CORRELATION_DIVERGENCE("MEDIUM", 60);

    /** Actor recorded on system-raised alerts. The sweep runs without a TrustContext. */
    public static final String SYSTEM_ACTOR = "system:emergency-alert-sweep";

    private final String severity;
    private final int responseWindowMinutes;

    EmergencyAlertType(String severity, int responseWindowMinutes) {
        this.severity = severity;
        this.responseWindowMinutes = responseWindowMinutes;
    }

    /** Matches the {@code chk_ea_severity} vocabulary. */
    public String severity() {
        return severity;
    }

    /** How long a responder has before the alert itself becomes OVERDUE. */
    public int responseWindowMinutes() {
        return responseWindowMinutes;
    }
}
