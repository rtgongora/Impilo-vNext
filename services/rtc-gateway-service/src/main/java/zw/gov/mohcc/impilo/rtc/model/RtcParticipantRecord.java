package zw.gov.mohcc.impilo.rtc.model;

import java.time.Instant;

/**
 * One lobby-tracked participant of an RTC session (rtc.session_participants).
 * Keyed on (sessionId, identity); state machine documented in V004.
 */
public record RtcParticipantRecord(
        String sessionId,
        String identity,
        String displayName,
        String role,
        String state,
        String mediaProfile,
        Instant requestedAt,
        Instant admittedAt,
        String admittedBy,
        String deniedReason
) {
    public static final String STATE_WAITING = "WAITING";
    public static final String STATE_ADMITTED = "ADMITTED";
    public static final String STATE_DENIED = "DENIED";
    public static final String STATE_CONNECTED = "CONNECTED";
    public static final String STATE_LEFT = "LEFT";

    /** Admitted at some point: token issuance is allowed (LEFT may reconnect). */
    public boolean tokenEligible() {
        return STATE_ADMITTED.equals(state) || STATE_CONNECTED.equals(state) || STATE_LEFT.equals(state);
    }
}
