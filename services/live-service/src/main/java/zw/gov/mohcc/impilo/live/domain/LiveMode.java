package zw.gov.mohcc.impilo.live.domain;

/**
 * Canonical Impilo Live engagement modes.
 *
 * <p>Impilo Live is the governed live engagement layer of the Impilo Health OS. The mode declares
 * the governed shape of a {@code LiveEvent} and drives the guardrails enforced by
 * {@link zw.gov.mohcc.impilo.live.core.LiveGovernanceGuard}. The mode is doctrine, not cosmetics:
 * clinical sessions are stricter than public broadcasts, and a mode cannot exist without its
 * owning workflow service.
 */
public enum LiveMode {

    /** Telemedicine / PCT clinical interaction requiring real-time media. Consent + clinical context mandatory. */
    CLINICAL_SESSION(ContextType.PROFESSIONAL, false, true),

    /** Internal MoHCC / facility / programme / partner coordination meeting. */
    PROFESSIONAL_MEETING(ContextType.PROFESSIONAL, true, false),

    /** Fundo-owned training, CPD, blended learning, community-of-practice webinar. */
    WEBINAR_CPD(ContextType.PROFESSIONAL, true, false),

    /** Public Health / Citizen Engagement broadcast: campaigns, launches, health education. */
    PUBLIC_BROADCAST(ContextType.PUBLIC, true, false),

    /** Physical event with online participation. */
    HYBRID_EVENT(ContextType.MIXED, true, false),

    /** Outbreak / disaster / urgent national health communication. */
    EMERGENCY_BRIEFING(ContextType.PUBLIC, true, false);

    private final ContextType defaultContextType;
    private final boolean recordingAllowedByDefault;
    private final boolean clinicalContextRequired;

    LiveMode(ContextType defaultContextType, boolean recordingAllowedByDefault, boolean clinicalContextRequired) {
        this.defaultContextType = defaultContextType;
        this.recordingAllowedByDefault = recordingAllowedByDefault;
        this.clinicalContextRequired = clinicalContextRequired;
    }

    public ContextType defaultContextType() {
        return defaultContextType;
    }

    /**
     * Whether recording may default to on. Patient-identifiable clinical recordings are disabled by
     * default unless an explicit recording policy permits them (doctrine §8).
     */
    public boolean recordingAllowedByDefault() {
        return recordingAllowedByDefault;
    }

    /** Whether the mode requires an owning clinical workflow, consent state and clinical identities. */
    public boolean clinicalContextRequired() {
        return clinicalContextRequired;
    }

    public boolean isPublicFacing() {
        return this == PUBLIC_BROADCAST || this == EMERGENCY_BRIEFING || this == HYBRID_EVENT;
    }

    /** Public broadcasts and emergency briefings must be moderated and carry verified official speakers. */
    public boolean moderationRequired() {
        return this == PUBLIC_BROADCAST || this == EMERGENCY_BRIEFING;
    }

    /** Official source / speaker verification is mandatory for public-facing official communications. */
    public boolean speakerVerificationRequired() {
        return this == PUBLIC_BROADCAST || this == EMERGENCY_BRIEFING;
    }

    public static LiveMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LiveMode.valueOf(value.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
    }

    /**
     * The platform SessionMode this LiveMode refines (session-template contract).
     * Drives which token-grant taxonomy the RTC Gateway applies to the room:
     * a WEBINAR_CPD classroom grants FACILITATOR/LEARNER publish rights, while
     * a broadcast's AUDIENCE tier is subscribe-only. LiveMode stays the
     * live-service-internal refinement; SessionMode is the cross-service key.
     */
    public String sessionMode() {
        return switch (this) {
            case CLINICAL_SESSION -> "TELEMEDICINE";
            case PROFESSIONAL_MEETING -> "MEETING";
            case WEBINAR_CPD -> "LEARNING_LIVE";
            case PUBLIC_BROADCAST, HYBRID_EVENT, EMERGENCY_BRIEFING -> "LIVE_EVENT";
        };
    }

    /**
     * Best-effort migration mapping from the legacy free-form {@code eventType} + {@code contextType}
     * + {@code organiserType} triad to a canonical mode. Used to backfill and to keep the legacy
     * create path working without a hard contract break.
     */
    public static LiveMode inferLegacy(String eventType, String contextType, String organiserType) {
        String et = eventType == null ? "" : eventType.toUpperCase();
        String ot = organiserType == null ? "" : organiserType.toUpperCase();
        String ct = contextType == null ? "" : contextType.toUpperCase();
        if (et.contains("CLINICAL") || et.contains("CONSULT") || et.contains("TELEMED")
                || et.contains("WARD") || et.contains("REFERRAL") || ot.contains("TELEMEDICINE")) {
            return CLINICAL_SESSION;
        }
        if (et.contains("EMERGENCY") || et.contains("OUTBREAK") || et.contains("BRIEFING")) {
            return EMERGENCY_BRIEFING;
        }
        if (et.contains("HYBRID")) {
            return HYBRID_EVENT;
        }
        if (et.contains("CPD") || et.contains("WEBINAR") || et.contains("TRAINING")
                || et.contains("COURSE") || et.contains("LESSON") || ot.contains("FUNDO")) {
            return WEBINAR_CPD;
        }
        if (et.contains("BROADCAST") || et.contains("CAMPAIGN") || et.contains("LAUNCH")
                || et.contains("EDUCATION") || ot.contains("PUBLIC_HEALTH") || "PUBLIC".equals(ct)) {
            return PUBLIC_BROADCAST;
        }
        if ("CITIZEN".equals(ct)) {
            return PUBLIC_BROADCAST;
        }
        return PROFESSIONAL_MEETING;
    }
}
