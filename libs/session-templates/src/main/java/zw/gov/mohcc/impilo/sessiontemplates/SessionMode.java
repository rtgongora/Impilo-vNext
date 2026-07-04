package zw.gov.mohcc.impilo.sessiontemplates;

/**
 * Cross-service key of the adaptive real-time session suite. live-service's
 * LiveMode remains its internal refinement; the template documents carry the
 * SessionMode↔LiveMode mapping.
 */
public enum SessionMode {
    MEETING,
    LIVE_EVENT,
    LEARNING_LIVE,
    LEARNING_RECORDING,
    TELEMEDICINE;

    /** Lenient parse for wire values; returns null when unknown. */
    public static SessionMode fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
